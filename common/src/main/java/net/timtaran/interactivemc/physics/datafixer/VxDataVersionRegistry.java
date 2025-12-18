/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.datafixer;

import com.google.common.annotations.VisibleForTesting;
import dev.architectury.platform.Platform;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages the mapping between semantic mod versions and linear integer schema versions.
 * This provides a stable, incremental version number for data persistence, independent
 * of the mod's public version string. This class must be initialized once at startup.
 *
 * @author xI-Mx-Ix
 */
public final class VxDataVersionRegistry {

    private static final Map<String, Integer> versionToSchemaMap = new ConcurrentHashMap<>();
    private static final AtomicInteger currentSchemaVersion = new AtomicInteger(-1);

    private VxDataVersionRegistry() {}

    /**
     * Registers a mapping between a specific mod version string and a data schema version.
     * This should be done for all mod versions that introduce a new schema.
     *
     * @param modVersion The semantic version string of the mod (e.g., "1.1.0").
     * @param schemaVersion The corresponding integer schema version.
     */
    public static void register(String modVersion, int schemaVersion) {
        if (versionToSchemaMap.putIfAbsent(modVersion, schemaVersion) != null) {
            System.err.println("Schema for mod version " + modVersion + " was already registered.");
        }
    }

    /**
     * Initializes the registry by detecting the current mod version via the Architectury
     * Platform API and looking up its corresponding schema version.
     *
     * @throws IllegalStateException if no schema is registered for the current mod version.
     */
    public static void initialize() {
        Platform.getOptionalMod("velthoric").ifPresent(mod -> {
            String version = mod.getVersion();
            Integer schema = versionToSchemaMap.get(version);
            if (schema == null) {
                throw new IllegalStateException("No data schema version registered for current mod version: " + version);
            }
            currentSchemaVersion.set(schema);
        });
    }

    /**
     * Gets the current data schema version for the running instance of the mod.
     *
     * @return The current schema version.
     * @throws IllegalStateException if the registry has not been initialized.
     */
    public static int getCurrentSchemaVersion() {
        int version = currentSchemaVersion.get();
        if (version == -1) {
            throw new IllegalStateException("VxDataVersionRegistry has not been initialized.");
        }
        return version;
    }

    /**
     * Resets the registry to its uninitialized state.
     * This method is public to be accessible from test source sets.
     */
    @VisibleForTesting
    public static void reset() {
        versionToSchemaMap.clear();
        currentSchemaVersion.set(-1);
    }
}