/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.gradle;

import net.xmx.velthoric.gradle.internal.CredentialService;
import net.xmx.velthoric.gradle.internal.PlatformConfigurator;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskProvider;

/**
 * The primary entry point for the Velthoric Publishing Plugin.
 * <p>
 * This plugin orchestrates the multi-platform publishing workflow. It distinguishes
 * between the root project (lifecycle management) and subprojects (artifact configuration).
 * <p>
 * Responsibilities:
 * <ul>
 *     <li><b>Root Project:</b> Registers the master lifecycle task that validates credentials.</li>
 *     <li><b>Subprojects:</b> Applies necessary third-party plugins, registers the configuration DSL,
 *     and configures the specific artifacts for upload.</li>
 * </ul>
 *
 * @author xI-Mx-Ix
 */
public class VelthoricPublishingPlugin implements Plugin<Project> {

    public static final String EXTENSION_NAME = "velthoricPublishing";
    public static final String LIFECYCLE_TASK_NAME = "publishVelthoric";
    public static final String SUBPROJECT_TASK_NAME = "publishMods";

    /**
     * Applies the plugin logic to the given project.
     * <p>
     * Differentiates between the root project and submodules to apply
     * the appropriate logic (orchestration vs. configuration).
     *
     * @param project The target project.
     */
    @Override
    public void apply(Project project) {
        if (project.equals(project.getRootProject())) {
            configureRootLifecycle(project);
        } else {
            configureSubmodule(project);
        }
    }

    /**
     * Registers the master lifecycle task on the root project.
     * <p>
     * This task acts as a gatekeeper. It runs AFTER the subprojects have executed their dry-runs.
     * <p>
     * <b>Filtering Logic:</b><br>
     * It strictly ignores any subproject that:
     * <ul>
     *     <li>Does not have the {@code velthoricPublishing} extension.</li>
     *     <li>Does NOT have an {@code artifact} configured (Missing DSL block).</li>
     *     <li>Has {@code dryRun = true}.</li>
     * </ul>
     * Only if a project has an artifact AND is not in dry-run mode, credentials are enforced.
     *
     * @param project The root project.
     */
    private void configureRootLifecycle(Project project) {
        project.getTasks().register(LIFECYCLE_TASK_NAME, task -> {
            task.setGroup("publishing");
            task.setDescription("Orchestrates the release pipeline, validating credentials and publishing all modules.");

            // Verification phase
            task.doFirst(t -> {
                boolean isRealRelease = project.getSubprojects().stream()
                        // 1. Get the extension (if present)
                        .map(sub -> sub.getExtensions().findByName(EXTENSION_NAME))
                        .filter(VelthoricExtension.class::isInstance)
                        .map(VelthoricExtension.class::cast)
                        // 2. Critical filter:
                        // - Ignore projects where 'artifact' is missing (User didn't configure the block).
                        // - Ignore projects where 'dryRun' is true.
                        // - If it's a snapshot, we only care about Maven (artifact).
                        .anyMatch(ext -> {
                            String modVersion = String.valueOf(project.findProperty("mod_version"));
                            boolean isSnapshot = ext.getSnapshot().get() || modVersion.endsWith("-SNAPSHOT");
                            boolean hasArtifact = ext.getArtifact().isPresent();
                            boolean isGitHubLead = ext.getPublishGitHub().get() && !isSnapshot;

                            return (hasArtifact || isGitHubLead) && !ext.getDryRun().get();
                        });

                if (isRealRelease) {
                    String modVersion = String.valueOf(project.findProperty("mod_version"));
                    boolean anySnapshot = project.getSubprojects().stream()
                            .map(sub -> sub.getExtensions().findByName(EXTENSION_NAME))
                            .filter(VelthoricExtension.class::isInstance)
                            .map(VelthoricExtension.class::cast)
                            .anyMatch(ext -> ext.getSnapshot().get() || modVersion.endsWith("-SNAPSHOT"));

                    CredentialService.validate(project, anySnapshot);
                    project.getLogger().lifecycle("Velthoric Release Pipeline: Credentials verified. Completing upload...");
                } else {
                    project.getLogger().lifecycle("Velthoric Release Pipeline: No active release detected (Dry Run or no artifacts). Skipping credential validation.");
                }
            });

            // Completion phase
            task.doLast(t -> project.getLogger().lifecycle("Velthoric Release Pipeline: Completed successfully."));
        });
    }

    /**
     * Applies the publishing configuration to a subproject.
     * <p>
     * This method:
     * <ol>
     *     <li>Creates the {@link VelthoricExtension} to expose the DSL.</li>
     *     <li>Applies the underlying 'mod-publish-plugin'.</li>
     *     <li>Checks if the user configured an artifact. If NOT, the module is ignored.</li>
     *     <li>Checks for required tokens; forces Dry Run if missing.</li>
     *     <li>Links the subproject's publish task to the root lifecycle task.</li>
     * </ol>
     *
     * @param project The subproject to configure.
     */
    private void configureSubmodule(Project project) {
        // 1. Register the DSL Extension exposed to build.gradle
        VelthoricExtension extension = project.getExtensions()
                .create(EXTENSION_NAME, VelthoricExtension.class);

        // 2. Apply dependencies (Third-party plugins required for uploading)
        project.getPlugins().apply("me.modmuss50.mod-publish-plugin");
        project.getPlugins().apply("maven-publish");

        // 3. Defer configuration logic until the build script has been evaluated.
        // This is necessary because we need to read the values set by the user in the 'velthoricPublishing' block.
        project.afterEvaluate(p -> {

            // A module is only active if it has an artifact
            // Or if it is explicitly designated to handle the GitHub release.
            boolean hasArtifact = extension.getArtifact().isPresent();
            boolean isGitHubLead = extension.getPublishGitHub().get();

            if (!hasArtifact && !isGitHubLead) {
                project.getLogger().debug("Velthoric: Skipping module '{}' - No artifact or GitHub release configured.", project.getName());
                return;
            }

            // If Modrinth or CurseForge tokens are invalid/missing, we automatically switch to Dry Run.
            // This prevents build failures due to missing credentials and allows for safe testing.
            String mrToken = CredentialService.get(p, CredentialService.KEY_MODRINTH);
            String cfToken = CredentialService.get(p, CredentialService.KEY_CURSEFORGE);
            String ghToken = CredentialService.get(p, CredentialService.KEY_GITHUB);
            String mavenUser = CredentialService.get(p, CredentialService.KEY_MAVEN_USER);
            String mavenToken = CredentialService.get(p, CredentialService.KEY_MAVEN_TOKEN);

            String modVersion = String.valueOf(p.getRootProject().findProperty("mod_version"));
            boolean isSnapshot = extension.getSnapshot().get() || modVersion.endsWith("-SNAPSHOT");

            if (mavenUser == null || mavenToken == null || (!isSnapshot && (mrToken == null || cfToken == null || ghToken == null))) {
                extension.getDryRun().set(true);
            }

            // --- Configuration ---

            // Configure Modrinth and CurseForge via the dedicated strategy class
            PlatformConfigurator.configure(p, extension);

            // Connect to Root Lifecycle
            p.getRootProject().getTasks().named(LIFECYCLE_TASK_NAME, rootTask -> {
                // Platforms (Modrinth/CurseForge/GitHub) - Skip if snapshot
                if (!isSnapshot) {
                    TaskProvider<Task> subTask = p.getTasks().named(SUBPROJECT_TASK_NAME);
                    rootTask.dependsOn(subTask);
                }

                // Also depend on Maven publishing if an artifact is present and we're not in dry run
                if (hasArtifact) {
                    String repoName = String.valueOf(p.findProperty("maven_repo_name"));
                    String taskName = "publishMavenJavaPublicationTo" + repoName + "Repository";
                    TaskProvider<Task> mavenTask = p.getTasks().named(taskName);
                    rootTask.dependsOn(p.provider(() -> extension.getDryRun().get() ? java.util.Collections.emptyList() : java.util.Collections.singletonList(mavenTask)));
                }

                String details = (hasArtifact ? (isSnapshot ? "Maven Snapshot" : "Platforms + Maven") : "") + (!isSnapshot && isGitHubLead ? (hasArtifact ? " + " : "") + "GitHub Only" : "");
                project.getLogger().lifecycle("Velthoric: Registered module '{}' for publication ({}).", project.getName(), details);
            });
        });
    }
}