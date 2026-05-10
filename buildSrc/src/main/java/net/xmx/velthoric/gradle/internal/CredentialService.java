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
 * Supports resolution via Gradle Properties and Environment Variables.
 */
public final class CredentialService {

    public static final String KEY_MODRINTH = "MODRINTH_TOKEN";
    public static final String KEY_CURSEFORGE = "CURSEFORGE_TOKEN";
    public static final String KEY_MAVEN_USER = "MAVEN_USER";
    public static final String KEY_MAVEN_TOKEN = "MAVEN_TOKEN";
    public static final String KEY_GITHUB = "GITHUB_TOKEN";

    private CredentialService() {}

    /**
     * Retrieves a token value. Checks Gradle properties first, then environment variables.
     *
     * @param project The Gradle project context.
     * @param key     The logical key name.
     * @return The resolved value, or null if missing.
     */
    @Nullable
    public static String get(Project project, String key) {
        // 1. Try Gradle Property (e.g. from gradle.properties or -Pkey=value)
        Object propValue = project.findProperty(key);
        if (propValue != null) {
            String val = String.valueOf(propValue);
            if (isValid(val)) {
                return val;
            }
        }

        // 2. Try Environment Variable
        String envValue = System.getenv(key);
        if (isValid(envValue)) {
            return envValue;
        }

        return null;
    }

    /**
     * Performs a strict validation of required release credentials.
     *
     * @param project The project context.
     * @throws IllegalStateException If any required token is missing.
     */
    public static void validate(Project project) {
        List<String> missing = new ArrayList<>();

        checkPresence(project, KEY_MODRINTH, missing);
        checkPresence(project, KEY_CURSEFORGE, missing);
        checkPresence(project, KEY_MAVEN_USER, missing);
        checkPresence(project, KEY_MAVEN_TOKEN, missing);

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