/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.config;

import com.google.gson.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * The Specification for the Configuration.
 * Handles defining the structure, loading from disk, and migrating versions.
 * <p>
 * This class manages the lifecycle of the config file, including atomic writes
 * to prevent data corruption and version-based migration logic.
 *
 * @author xI-Mx-Ix
 */
public class VxConfigSpec {

    private final List<VxConfigValue<?>> values;
    private final String version;
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    private static final String VERSION_KEY = "_version";

    private VxConfigSpec(List<VxConfigValue<?>> values, String version) {
        this.values = values;
        this.version = version;
    }

    /**
     * Loads the configuration from the file system.
     * <p>
     * Logic Flow:
     * 1. Attempts to read the existing JSON file.
     * 2. Checks if the version in the file matches the runtime version.
     * 3. If version mismatches or file is missing, generates a fresh structure based on defaults.
     * 4. If migrating, copies values from the old file if the keys still exist in the new structure.
     * 5. Atomically writes the result to disk if changes occurred.
     * 6. Updates all {@link VxConfigValue} references in memory.
     *
     * @param path The path to the config file.
     */
    public void load(Path path) {
        JsonObject fileJson = null;
        boolean needsWrite = false;

        // 1. Try to read existing file
        if (Files.exists(path)) {
            try {
                fileJson = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            } catch (Exception e) {
                // Log failure but proceed to overwrite with defaults to restore functionality
                System.err.println("VxConfig: Failed to parse config file, resetting to defaults. " + e.getMessage());
                fileJson = null;
            }
        }

        // 2. Check version and determine if we need to migrate/rewrite
        String fileVersion = (fileJson != null && fileJson.has(VERSION_KEY))
                ? fileJson.get(VERSION_KEY).getAsString()
                : "0.0.0";

        if (fileJson == null || !fileVersion.equals(this.version)) {
            needsWrite = true;
        }

        // 3. Construct the "Ideal" JSON structure based on the current Spec (Code)
        JsonObject idealJson = new JsonObject();
        idealJson.addProperty(VERSION_KEY, this.version);

        for (VxConfigValue<?> valueSpec : values) {
            writeToTree(idealJson, valueSpec.getPath(), valueSpec.getDefault(), valueSpec.getComment());
        }

        // 4. If rewriting/migrating, attempt to recover values from the old fileJson
        if (needsWrite && fileJson != null) {
            mergeValues(idealJson, fileJson);
        } else if (!needsWrite) {
            // If versions match, we trust the fileJson as the source of truth
            idealJson = fileJson;
        }

        // 5. Update Memory & Write to Disk if necessary
        updateMemoryValues(idealJson);

        if (needsWrite) {
            try {
                atomicWrite(path, idealJson);
            } catch (IOException e) {
                throw new RuntimeException("VxConfig: Failed to write config file", e);
            }
        }
    }

    /**
     * Recursively traverses the Ideal JSON. If a value exists in the Old JSON at the same path,
     * it overwrites the Ideal JSON's default value.
     * <p>
     * This logic ensures that obsolete keys are removed (since we iterate over the Ideal keys)
     * and new keys are added (since they exist in Ideal), while preserving user data.
     *
     * @param target The new "Ideal" JSON structure.
     * @param source The old JSON structure loaded from disk.
     */
    private void mergeValues(JsonObject target, JsonObject source) {
        for (String key : target.keySet()) {
            if (key.equals(VERSION_KEY)) continue;

            JsonElement targetElem = target.get(key);
            JsonElement sourceElem = source.get(key);

            if (sourceElem != null) {
                if (targetElem.isJsonObject() && sourceElem.isJsonObject()) {
                    // Recurse into nested categories
                    mergeValues(targetElem.getAsJsonObject(), sourceElem.getAsJsonObject());
                } else if (!targetElem.isJsonObject() && !sourceElem.isJsonObject()) {
                    // Transfer the value.
                    // We explicitly exclude comment fields from the copy process to ensure
                    // comments are always updated from the code.
                    if (!key.startsWith("_comment_")) {
                        target.add(key, sourceElem);
                    }
                }
            }
        }
    }

    /**
     * Updates the runtime objects (VxConfigValue) with data from the final JSON object.
     * Uses a helper method to handle the wildcard type capture safely.
     *
     * @param root The root of the configuration JSON.
     */
    private void updateMemoryValues(JsonObject root) {
        for (VxConfigValue<?> configValue : values) {
            JsonElement element = findElement(root, configValue.getPath());

            if (element != null && !element.isJsonNull()) {
                Object parsed = parseElement(element, configValue.getType());
                if (parsed != null) {
                    updateValueCapture(configValue, parsed);
                }
            }
        }
    }

    /**
     * Helper method to capture the wildcard type 'T' and ensure type safety when setting the value.
     */
    @SuppressWarnings("unchecked")
    private <T> void updateValueCapture(VxConfigValue<T> valueSpec, Object parsedValue) {
        valueSpec.set((T) parsedValue);
    }

    /**
     * Navigates the JSON tree to find the element at the specified path.
     */
    private JsonElement findElement(JsonObject root, List<String> path) {
        JsonObject current = root;
        for (int i = 0; i < path.size(); i++) {
            String segment = path.get(i);
            if (!current.has(segment)) return null;

            if (i == path.size() - 1) {
                return current.get(segment);
            } else {
                JsonElement child = current.get(segment);
                if (!child.isJsonObject()) return null;
                current = child.getAsJsonObject();
            }
        }
        return null;
    }

    /**
     * Parses a JsonElement into the corresponding Java object based on the expected class type.
     */
    private Object parseElement(JsonElement element, Class<?> type) {
        try {
            if (type == Integer.class) return element.getAsInt();
            if (type == Boolean.class) return element.getAsBoolean();
            if (type == Double.class) return element.getAsDouble();
            if (type == Long.class) return element.getAsLong();
            if (type == Float.class) return element.getAsFloat();
            return element.getAsString();
        } catch (Exception e) {
            // Fallback if the user typed "true" for an Integer field, etc.
            System.err.println("VxConfig: Type mismatch for config entry. Using default. " + e.getMessage());
            return null;
        }
    }

    /**
     * Writes a value into the JsonObject tree, creating parent objects if necessary.
     * Also adds a sibling key "_comment_{name}" to simulate comments in JSON.
     */
    private void writeToTree(JsonObject root, List<String> path, Object value, String comment) {
        JsonObject current = root;
        for (int i = 0; i < path.size() - 1; i++) {
            String segment = path.get(i);
            if (!current.has(segment)) {
                current.add(segment, new JsonObject());
            }
            current = current.getAsJsonObject(segment);
        }

        String key = path.get(path.size() - 1);

        current.add(key, GSON.toJsonTree(value));

        if (comment != null && !comment.isEmpty()) {
            current.addProperty("_comment_" + key, comment);
        }
    }

    /**
     * Performs an atomic write using a temporary file.
     * This ensures that if the game crashes during write, the config file is not left in a corrupted state.
     */
    private void atomicWrite(Path path, JsonObject json) throws IOException {
        Path tempPath = path.resolveSibling(path.getFileName() + ".tmp");
        if (path.getParent() != null) Files.createDirectories(path.getParent());

        Files.writeString(tempPath, GSON.toJson(json));
        Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    // ================== BUILDER ==================

    /**
     * Builder class for constructing the VxConfigSpec.
     * Follows the standard builder pattern with stack-based categorization.
     */
    public static class Builder {
        private final List<VxConfigValue<?>> definedValues = new ArrayList<>();
        private final Stack<String> pathStack = new Stack<>();
        private String version = "1.0.0";

        public Builder() {
        }

        /**
         * Sets the version for the configuration spec.
         * This determines if a file rewrite/migration is triggered.
         *
         * @param version The version string (typically Mod Version).
         * @return The builder instance.
         */
        public Builder setVersion(String version) {
            this.version = version;
            return this;
        }

        /**
         * Pushes a category onto the stack. All subsequent definitions will be nested under this category.
         *
         * @param category The name of the category.
         * @return The builder instance.
         */
        public Builder push(String category) {
            pathStack.push(category);
            return this;
        }

        /**
         * Pops the current category off the stack.
         *
         * @return The builder instance.
         */
        public Builder pop() {
            if (!pathStack.isEmpty()) {
                pathStack.pop();
            }
            return this;
        }

        /**
         * Defines a configuration value with a default value and a comment.
         *
         * @param name         The key name of the value.
         * @param defaultValue The default value.
         * @param comment      Description of the value.
         * @param <T>          The type of the value.
         * @return A wrapper object holding the configuration value.
         */
        public <T> VxConfigValue<T> define(String name, T defaultValue, String comment) {
            List<String> fullPath = new ArrayList<>(pathStack);
            fullPath.add(name);

            @SuppressWarnings("unchecked")
            Class<T> type = (Class<T>) defaultValue.getClass();

            VxConfigValue<T> val = new VxConfigValue<>(fullPath, defaultValue, comment, type);
            definedValues.add(val);
            return val;
        }

        /**
         * Defines a configuration value without a comment.
         *
         * @param name         The key name.
         * @param defaultValue The default value.
         * @param <T>          The type of the value.
         * @return A wrapper object holding the configuration value.
         */
        public <T> VxConfigValue<T> define(String name, T defaultValue) {
            return define(name, defaultValue, null);
        }

        /**
         * Defines an integer value with an implied range in the comment.
         *
         * @param name         The key name.
         * @param defaultValue The default value.
         * @param min          Minimum value (inclusive).
         * @param max          Maximum value (inclusive).
         * @param comment      Description.
         * @return A wrapper object holding the integer.
         */
        public VxConfigValue<Integer> defineInRange(String name, int defaultValue, int min, int max, String comment) {
            return define(name, defaultValue, comment + " [Range: " + min + " ~ " + max + "]");
        }

        /**
         * Defines a double value with an implied range in the comment.
         *
         * @param name         The key name.
         * @param defaultValue The default value.
         * @param min          Minimum value (inclusive).
         * @param max          Maximum value (inclusive).
         * @param comment      Description.
         * @return A wrapper object holding the double.
         */
        public VxConfigValue<Double> defineInRange(String name, double defaultValue, double min, double max, String comment) {
            return define(name, defaultValue, comment + " [Range: " + min + " ~ " + max + "]");
        }

        /**
         * Defines a long value with an implied range in the comment.
         *
         * @param name         The key name.
         * @param defaultValue The default value.
         * @param min          Minimum value (inclusive).
         * @param max          Maximum value (inclusive).
         * @param comment      Description.
         * @return A wrapper object holding the long.
         */
        public VxConfigValue<Long> defineInRange(String name, long defaultValue, long min, long max, String comment) {
            return define(name, defaultValue, comment + " [Range: " + min + " ~ " + max + "]");
        }

        /**
         * Finalizes the build process and creates the specification.
         *
         * @return The immutable VxConfigSpec instance.
         */
        public VxConfigSpec build() {
            return new VxConfigSpec(definedValues, version);
        }
    }
}