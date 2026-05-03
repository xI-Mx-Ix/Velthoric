/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.gradle.internal;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for resolving and validating sensitive credentials.
 * Supports resolution only via Environment Variables.
 */
public final class CredentialService {

    public static final String KEY_MAVEN_REPO = "MAVEN_REPO";
    public static final String KEY_MAVEN_USER = "MAVEN_USER";
    public static final String KEY_MAVEN_TOKEN = "MAVEN_TOKEN";
    public static final String KEY_MODRINTH = "MODRINTH_TOKEN";
    public static final String KEY_CURSEFORGE = "CURSEFORGE_TOKEN";

    private CredentialService() {}

    /**
     * Retrieves a token value from environment variables.
     *
     * @param key The logical key name.
     * @return The resolved value, or null if missing.
     */
    @Nullable
    public static String get(String key) {
        String envValue = System.getenv(key);
        if (isValid(envValue)) {
            return envValue;
        }
        return null;
    }

    /**
     * Performs a strict validation of required release credentials.
     *
     * @throws IllegalStateException If any required token is missing.
     */
    public static void validate() {
        List<String> missing = new ArrayList<>();

        checkPresence(KEY_MAVEN_REPO, missing);
        checkPresence(KEY_MAVEN_USER, missing);
        checkPresence(KEY_MAVEN_TOKEN, missing);
        checkPresence(KEY_MODRINTH, missing);
        checkPresence(KEY_CURSEFORGE, missing);

        if (!missing.isEmpty()) {
            throw new IllegalStateException("Velthoric Release Failed: Missing credentials for: " + String.join(", ", missing));
        }
    }

    private static void checkPresence(String key, List<String> missingCollector) {
        if (get(key) == null) {
            missingCollector.add(key);
        }
    }

    private static boolean isValid(String value) {
        return value != null && !value.isBlank();
    }
}