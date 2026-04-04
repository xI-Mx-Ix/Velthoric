/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.tool.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents the configuration data for a tool.
 * <p>
 * This class manages a collection of typed properties, allowing for the storage
 * and retrieval of various primitive types and Strings.
 *
 * @author xI-Mx-Ix
 */
public class VxToolConfig {

    private final Map<String, VxToolProperty<?>> properties = new LinkedHashMap<>();

    // --- Integer ---

    /**
     * Adds an integer property.
     *
     * @param key          The unique identifier for the property.
     * @param defaultValue The default value.
     * @param min          The minimum allowed value.
     * @param max          The maximum allowed value.
     */
    public void addInt(String key, int defaultValue, int min, int max) {
        properties.put(key, new VxToolProperty<>(key, defaultValue, min, max, Integer.class));
    }

    /**
     * Retrieves an integer property value.
     *
     * @param key The property key.
     * @return The integer value.
     */
    public int getInt(String key) {
        return (int) properties.get(key).getValue();
    }

    // --- Long ---

    /**
     * Adds a long integer property.
     *
     * @param key          The unique identifier for the property.
     * @param defaultValue The default value.
     * @param min          The minimum allowed value.
     * @param max          The maximum allowed value.
     */
    public void addLong(String key, long defaultValue, long min, long max) {
        properties.put(key, new VxToolProperty<>(key, defaultValue, min, max, Long.class));
    }

    /**
     * Retrieves a long property value.
     *
     * @param key The property key.
     * @return The long value.
     */
    public long getLong(String key) {
        return (long) properties.get(key).getValue();
    }

    // --- Float ---

    /**
     * Adds a float property.
     *
     * @param key          The unique identifier for the property.
     * @param defaultValue The default value.
     * @param min          The minimum allowed value.
     * @param max          The maximum allowed value.
     */
    public void addFloat(String key, float defaultValue, float min, float max) {
        properties.put(key, new VxToolProperty<>(key, defaultValue, min, max, Float.class));
    }

    /**
     * Retrieves a float property value.
     *
     * @param key The property key.
     * @return The float value.
     */
    public float getFloat(String key) {
        return (float) properties.get(key).getValue();
    }

    // --- Double ---

    /**
     * Adds a double property.
     *
     * @param key          The unique identifier for the property.
     * @param defaultValue The default value.
     * @param min          The minimum allowed value.
     * @param max          The maximum allowed value.
     */
    public void addDouble(String key, double defaultValue, double min, double max) {
        properties.put(key, new VxToolProperty<>(key, defaultValue, min, max, Double.class));
    }

    /**
     * Retrieves a double property value.
     *
     * @param key The property key.
     * @return The double value.
     */
    public double getDouble(String key) {
        return (double) properties.get(key).getValue();
    }

    // --- Boolean ---

    /**
     * Adds a boolean property.
     *
     * @param key          The unique identifier for the property.
     * @param defaultValue The default value.
     */
    public void addBoolean(String key, boolean defaultValue) {
        // Min and max are irrelevant for boolean, passing 0 as placeholders.
        properties.put(key, new VxToolProperty<>(key, defaultValue, 0, 0, Boolean.class));
    }

    /**
     * Retrieves a boolean property value.
     *
     * @param key The property key.
     * @return The boolean value.
     */
    public boolean getBoolean(String key) {
        return (boolean) properties.get(key).getValue();
    }

    // --- String ---

    /**
     * Adds a string property.
     *
     * @param key          The unique identifier for the property.
     * @param defaultValue The default value.
     */
    public void addString(String key, String defaultValue) {
        // Min and max are irrelevant for string, passing null.
        properties.put(key, new VxToolProperty<>(key, defaultValue, null, null, String.class));
    }

    /**
     * Retrieves a string property value.
     *
     * @param key The property key.
     * @return The string value.
     */
    public String getString(String key) {
        return (String) properties.get(key).getValue();
    }

    // --- General Access ---

    /**
     * Updates the value of an existing property.
     *
     * @param key   The property key.
     * @param value The new value (must match the property's type).
     */
    public void setValue(String key, Object value) {
        if (properties.containsKey(key)) {
            properties.get(key).setValue(value);
        }
    }

    /**
     * Returns the raw map of properties.
     *
     * @return The map containing all registered properties.
     */
    public Map<String, VxToolProperty<?>> getProperties() {
        return properties;
    }

    /**
     * Resets all configuration properties to their default values.
     */
    public void resetToDefaults() {
        for (VxToolProperty<?> prop : properties.values()) {
            prop.setValue(prop.getDefaultValue());
        }
    }

    /**
     * Loads the configuration from the client's local storage.
     *
     * @param toolId The ID of the tool.
     */
    public void load(String toolId) {
        VxToolPersistence.load(toolId, this);
    }

    /**
     * Saves the configuration to the client's local storage.
     *
     * @param toolId The ID of the tool.
     */
    public void save(String toolId) {
        VxToolPersistence.save(toolId, this);
    }

    /**
     * Applies a map of string-represented values to the configuration.
     *
     * @param edits A map of keys to string values.
     */
    public void applyEdits(Map<String, String> edits) {
        for (Map.Entry<String, String> entry : edits.entrySet()) {
            String key = entry.getKey();
            String valStr = entry.getValue();

            if (properties.containsKey(key)) {
                VxToolProperty<?> prop = properties.get(key);
                try {
                    Class<?> type = prop.getType();
                    if (type == Integer.class) {
                        setValue(key, Integer.parseInt(valStr));
                    } else if (type == Long.class) {
                        setValue(key, Long.parseLong(valStr));
                    } else if (type == Float.class) {
                        setValue(key, Float.parseFloat(valStr));
                    } else if (type == Double.class) {
                        setValue(key, Double.parseDouble(valStr));
                    } else if (type == Boolean.class) {
                        setValue(key, Boolean.parseBoolean(valStr));
                    } else if (type == String.class) {
                        setValue(key, valStr);
                    }
                } catch (NumberFormatException ignored) {
                    // Skip invalid inputs
                }
            }
        }
    }
}