/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.tool.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents the configuration data for a tool.
 *
 * @author xI-Mx-Ix
 */
public class VxToolConfig {

    private final Map<String, VxToolProperty<?>> properties = new LinkedHashMap<>();

    public void addFloat(String key, float defaultValue, float min, float max) {
        properties.put(key, new VxToolProperty<>(key, defaultValue, min, max, Float.class));
    }

    public void addInt(String key, int defaultValue, int min, int max) {
        properties.put(key, new VxToolProperty<>(key, defaultValue, min, max, Integer.class));
    }

    public void addBoolean(String key, boolean defaultValue) {
        properties.put(key, new VxToolProperty<>(key, defaultValue, 0, 0, Boolean.class));
    }

    public float getFloat(String key) {
        return (float) properties.get(key).getValue();
    }

    public int getInt(String key) {
        return (int) properties.get(key).getValue();
    }

    public boolean getBoolean(String key) {
        return (boolean) properties.get(key).getValue();
    }

    public void setValue(String key, Object value) {
        if (properties.containsKey(key)) {
            properties.get(key).setValue(value);
        }
    }

    public Map<String, VxToolProperty<?>> getProperties() {
        return properties;
    }
}