/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.gradle;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;

import javax.inject.Inject;

/**
 * Configuration DSL exposed to build scripts via the 'velthoricPublishing' block.
 * <p>
 * This class uses Gradle's {@link Property} API to ensure lazy evaluation and
 * compatibility with the configuration cache.
 * <p>
 * Usage example in build.gradle:
 * <pre>
 * velthoricPublishing {
 *     loader = "fabric"
 *     artifact = tasks.named("remapJar")
 *     dryRun = true
 *     // Optional: enableCloudsmith = true
 * }
 * </pre>
 *
 * @author xI-Mx-Ix
 */
public abstract class VelthoricExtension {

    /**
     * The mod loader identifier (e.g., "fabric", "neoforge", "forge").
     * <p>
     * This property is critical as it drives the {@code ModLoader} strategy, determining:
     * <ul>
     *     <li>Which dependencies are required (e.g., fabric-api).</li>
     *     <li>Which tags are applied to the upload.</li>
     *     <li>The default display name (e.g., "Fabric" vs "NeoForge").</li>
     * </ul>
     */
    @Input
    public abstract Property<String> getLoader();

    /**
     * Optional override for the platform name in the file title.
     * <p>
     * If not set, this defaults to the standard pretty-name defined by the loader strategy.
     * <br>
     * Example: If loader is "neoforge", this defaults to "NeoForge".
     */
    @Input
    @Optional
    public abstract Property<String> getDisplayName();

    /**
     * The primary artifact to publish.
     * <p>
     * This property accepts various types:
     * <ul>
     *     <li>{@code TaskProvider} (e.g., tasks.named("remapJar")) - Recommended.</li>
     *     <li>{@code AbstractArchiveTask} (e.g., remapJar).</li>
     *     <li>{@code File} objects.</li>
     * </ul>
     */
    @Internal
    public abstract Property<Object> getArtifact();

    /**
     * Whether to execute a dry run.
     * <p>
     * If set to {@code true}, the publishing tasks will run validation logic and print
     * what would happen, but will <b>not</b> actually upload any files to remote servers.
     * Defaults to {@code false}.
     */
    @Input
    public abstract Property<Boolean> getDryRun();

    /**
     * Controls whether to configure the Cloudsmith Maven repository.
     * <p>
     * If {@code true}, the plugin will attempt to configure the 'maven-publish' extension
     * with Velthoric's Cloudsmith credentials.
     * <p>
     * Defaults to {@code false} to prevent accidental snapshots or private uploads.
     */
    @Input
    public abstract Property<Boolean> getEnableCloudsmith();

    /**
     * The Project ID used for CurseForge uploads.
     * <p>
     * Defaults to the main Velthoric ID ("1367260"), but can be overridden if a submodule
     * belongs to a different project page.
     */
    @Input
    public abstract Property<String> getCurseProjectId();

    /**
     * The Project ID used for Modrinth uploads.
     * <p>
     * Defaults to the main Velthoric slug ("velthoric"), but can be overridden if a submodule
     * belongs to a different project page.
     */
    @Input
    public abstract Property<String> getModrinthProjectId();

    /**
     * Injection constructor.
     * Initializes the properties with safe default conventions.
     *
     * @param objects The Gradle ObjectFactory used to create properties.
     */
    @Inject
    public VelthoricExtension(ObjectFactory objects) {
        // Set conventions (default values)
        getLoader().convention("common");
        getDisplayName().convention("Common"); // Fallback if loader strategy fails or is common
        getDryRun().convention(false);
        getEnableCloudsmith().convention(false); // Default: Don't publish to Cloudsmith

        // Default IDs for the Velthoric project
        getCurseProjectId().convention("1367260");
        getModrinthProjectId().convention("i2yo1LYg");
    }
}