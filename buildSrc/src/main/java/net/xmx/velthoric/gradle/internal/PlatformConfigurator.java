/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.gradle.internal;

import me.modmuss50.mpp.ModPublishExtension;
import me.modmuss50.mpp.ReleaseType;
import net.xmx.velthoric.gradle.VelthoricExtension;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

/**
 * Handles the mapping between Velthoric's simplified DSL and the underlying publishing plugin.
 *
 * @author xI-Mx-Ix
 */
public final class PlatformConfigurator {

    private PlatformConfigurator() {}

    /**
     * Configures the publishing extension.
     *
     * @param project   The Gradle project.
     * @param extension The user configuration.
     */
    public static void configure(Project project, VelthoricExtension extension) {
        if (!extension.getArtifact().isPresent()) {
            project.getLogger().info("Velthoric: No artifact configured for {}, skipping publication setup.", project.getName());
            return;
        }

        // 1. Resolve Strategy
        ModLoader loader = ModLoader.from(extension.getLoader().get());

        // 2. Resolve Metadata
        String modVersion = String.valueOf(project.getRootProject().findProperty("mod_version"));
        String mcVersion = String.valueOf(project.getRootProject().findProperty("minecraft_version"));
        String changelog = ChangelogService.resolveChangelog(project.getRootProject(), modVersion);

        // 3. Determine Human-Readable Name
        String platformName = extension.getDisplayName().isPresent()
                ? extension.getDisplayName().get()
                : loader.getPrettyName();

        String finalTitle = String.format("Velthoric %s for %s %s", modVersion, platformName, mcVersion);

        // 4. Configure Third-Party Extension
        ModPublishExtension publishExt = project.getExtensions().getByType(ModPublishExtension.class);

        publishExt.getDryRun().set(extension.getDryRun());
        publishExt.getChangelog().set(changelog);
        publishExt.getVersion().set(modVersion);
        publishExt.getDisplayName().set(finalTitle);

        // 5. Artifact Resolution
        // We normalize whatever the user passed (Task, Provider, File) into a single, clean Provider<RegularFile>.
        Provider<RegularFile> artifactFile = normalizeArtifact(project, extension.getArtifact().get());
        publishExt.getFile().set(artifactFile);

        // 6. Platform Specifics
        configureCurseForge(project, publishExt, extension, loader, mcVersion);
        configureModrinth(project, publishExt, extension, loader, mcVersion);
    }

    /**
     * Normalizes the user input into a standardized Provider<RegularFile>.
     * <p>
     * This handles:
     * <ul>
     *     <li>{@code TaskProvider} (e.g., tasks.named("remapJar")) - Extracts the archiveFile property.</li>
     *     <li>{@code AbstractArchiveTask} - Extracts the archiveFile property.</li>
     *     <li>{@code File} - Converts to a RegularFile provider.</li>
     * </ul>
     *
     * @param project The project context.
     * @param input   The raw input object from the extension.
     * @return A lazy provider pointing to the final jar file.
     * @throws GradleException If the input type is invalid or the task is not an archive task.
     */
    private static Provider<RegularFile> normalizeArtifact(Project project, Object input) {
        // Case A: The user passed a TaskProvider (e.g., tasks.named("remapJar"))
        if (input instanceof TaskProvider<?> taskProvider) {
            return taskProvider.flatMap(task -> {
                if (task instanceof AbstractArchiveTask archiveTask) {
                    return archiveTask.getArchiveFile();
                }
                throw new GradleException(
                        "The task '" + task.getName() + "' provided as an artifact is not an Archive Task (Jar/Zip). " +
                                "It is of type: " + task.getClass().getName()
                );
            });
        }

        // Case B: The user passed a concrete Task (e.g., tasks.remapJar)
        if (input instanceof AbstractArchiveTask archiveTask) {
            return archiveTask.getArchiveFile();
        }

        // Case C: The user passed a File, Path, or String
        // layout.file() handles resolving Files and Strings relative to the project directory.
        return project.getLayout().file(project.provider(() -> project.file(input)));
    }

    private static void configureCurseForge(Project project, ModPublishExtension ext, VelthoricExtension velthoric, ModLoader loader, String mcVersion) {
        ext.curseforge(cf -> {
            cf.getAccessToken().set(CredentialService.get(project, CredentialService.KEY_CURSEFORGE));
            cf.getProjectId().set(velthoric.getCurseProjectId());
            cf.getType().set(ReleaseType.STABLE);
            cf.getMinecraftVersions().add(mcVersion);

            cf.getModLoaders().addAll(loader.getCurseTags());

            cf.getClientRequired().set(true);
            cf.getServerRequired().set(true);

            String[] deps = loader.getRequiredDependencies().toArray(new String[0]);
            if (deps.length > 0) {
                cf.requires(deps);
            }
        });
    }

    private static void configureModrinth(Project project, ModPublishExtension ext, VelthoricExtension velthoric, ModLoader loader, String mcVersion) {
        ext.modrinth(mr -> {
            mr.getAccessToken().set(CredentialService.get(project, CredentialService.KEY_MODRINTH));
            mr.getProjectId().set(velthoric.getModrinthProjectId());
            mr.getType().set(ReleaseType.BETA);
            mr.getMinecraftVersions().add(mcVersion);

            mr.getModLoaders().addAll(loader.getModrinthTags());

            String[] deps = loader.getRequiredDependencies().toArray(new String[0]);
            if (deps.length > 0) {
                mr.requires(deps);
            }
        });
    }
}