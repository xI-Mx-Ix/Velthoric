/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.gradle.internal;

import java.util.List;
import java.util.Locale;

/**
 * Represents supported mod loaders and their specific platform configurations.
 * This enum acts as a strategy for applying loader-specific metadata.
 *
 * @author xI-Mx-Ix
 */
public enum ModLoader {

    FABRIC(
        "Fabric",
        List.of("fabric", "quilt"),
        List.of("fabric", "quilt"),
        List.of("architectury-api", "fabric-api")
    ),

    FORGE(
        "Forge",
        List.of("forge"),
        List.of("forge"),
        List.of("architectury-api")
    ),

    NEOFORGE(
        "NeoForge",
        List.of("neoforge"),
        List.of("neoforge"),
        List.of("architectury-api")
    );

    private final String prettyName;
    private final List<String> curseTags;
    private final List<String> modrinthTags;
    private final List<String> requiredDependencies;

    ModLoader(String prettyName, List<String> curseTags, List<String> modrinthTags, List<String> requiredDependencies) {
        this.prettyName = prettyName;
        this.curseTags = curseTags;
        this.modrinthTags = modrinthTags;
        this.requiredDependencies = requiredDependencies;
    }

    /**
     * Returns the human-readable name properly capitalized (e.g., "NeoForge").
     *
     * @return The display string.
     */
    public String getPrettyName() {
        return prettyName;
    }

    public List<String> getCurseTags() {
        return curseTags;
    }

    public List<String> getModrinthTags() {
        return modrinthTags;
    }

    public List<String> getRequiredDependencies() {
        return requiredDependencies;
    }

    /**
     * Resolves a ModLoader from a string (case-insensitive).
     *
     * @param name The loader name (e.g., "neoforge").
     * @return The matching ModLoader.
     * @throws IllegalArgumentException if the loader is unknown.
     */
    public static ModLoader from(String name) {
        if (name == null) throw new IllegalArgumentException("Loader cannot be null");
        try {
            return ModLoader.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported or unknown loader type: '" + name + "'. " +
                    "Valid options are: " + String.join(", ", List.of("fabric", "forge", "neoforge")));
        }
    }
}