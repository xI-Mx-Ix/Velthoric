/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.gradle;

import net.xmx.velthoric.gradle.internal.CredentialService;
import net.xmx.velthoric.gradle.internal.MavenConfigurator;
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
                        .anyMatch(ext -> ext.getArtifact().isPresent() && !ext.getDryRun().get());

                if (isRealRelease) {
                    CredentialService.validate(project);
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
     *     <li>Only links the subproject's publish task to the root lifecycle task if an artifact exists.</li>
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

        // 3. Defer configuration logic until the build script has been evaluated.
        // This is necessary because we need to read the values set by the user in the 'velthoricPublishing' block.
        project.afterEvaluate(p -> {

            // If the user did not specify an artifact (e.g. no velthoricPublishing block or empty block),
            // we strictly ignore this module. It will not be configured, and it will NOT be linked
            // to the root publishing task.
            if (!extension.getArtifact().isPresent()) {
                project.getLogger().debug("Velthoric: Skipping module '{}' - No artifact configured.", project.getName());
                return;
            }

            // --- Everything below this line only happens if the block matches requirements ---

            // Configure Cloudsmith Maven repository (only if enabled in extension)
            MavenConfigurator.configure(p, extension);

            // Configure Modrinth and CurseForge via the dedicated strategy class
            PlatformConfigurator.configure(p, extension);

            // Connect to Root Lifecycle
            // We do this HERE instead of earlier, so that modules without artifacts are never part of the dependency graph.
            p.getRootProject().getTasks().named(LIFECYCLE_TASK_NAME, rootTask -> {
                TaskProvider<Task> subTask = p.getTasks().named(SUBPROJECT_TASK_NAME);
                rootTask.dependsOn(subTask);
                project.getLogger().lifecycle("Velthoric: Registered module '{}' for publication.", project.getName());
            });
        });
    }
}