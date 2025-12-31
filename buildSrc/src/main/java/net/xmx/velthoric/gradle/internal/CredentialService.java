/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.gradle.internal;

import org.gradle.api.Project;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for resolving and validating sensitive credentials.
 * Supports resolution via Environment Variables (priority) and Gradle Properties.
 *
 * @author xI-Mx-Ix
 */
public final class CredentialService {

    public static final String KEY_GITHUB = "github_token";
    public static final String KEY_MODRINTH = "modrinth_token";
    public static final String KEY_CURSEFORGE = "curseforge_token";
    public static final String KEY_CLOUDSMITH_USER = "cloudsmith_user";
    public static final String KEY_CLOUDSMITH_KEY = "cloudsmith_key";

    private CredentialService() {}

    /**
     * Retrieves a token or property value.
     *
     * @param project The project context.
     * @param key     The logical key name (e.g., "modrinth_token").
     * @return The resolved value, or null if missing.
     */
    @Nullable
    public static String get(Project project, String key) {
        // 1. Environment Variable Strategy (UPPER_CASE)
        String envKey = key.toUpperCase();
        String envValue = System.getenv(envKey);
        if (isValid(envValue)) {
            return envValue;
        }

        // 2. Gradle Property Strategy (gradle.properties)
        if (project.hasProperty(key)) {
            Object prop = project.property(key);
            if (prop != null && isValid(prop.toString())) {
                return prop.toString();
            }
        }

        return null; // Not found
    }

    /**
     * Performs a strict validation of required release credentials.
     *
     * @param project The project context to query.
     * @throws IllegalStateException If any required token is missing.
     */
    public static void validate(Project project) {
        List<String> missing = new ArrayList<>();

        checkPresence(project, KEY_GITHUB, missing);
        checkPresence(project, KEY_MODRINTH, missing);
        checkPresence(project, KEY_CURSEFORGE, missing);

        if (!missing.isEmpty()) {
            throw new IllegalStateException("Velthoric Release Failed: Missing credentials for: " + String.join(", ", missing));
        }
    }

    private static void checkPresence(Project project, String key, List<String> missingCollector) {
        if (get(project, key) == null) {
            missingCollector.add(key);
        }
    }

    private static boolean isValid(String value) {
        return value != null && !value.isBlank();
    }
}