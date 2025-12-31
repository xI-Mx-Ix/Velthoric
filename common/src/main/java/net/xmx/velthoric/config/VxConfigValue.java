/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.config;

import java.util.List;
import java.util.function.Supplier;

/**
 * Represents a configuration value wrapper.
 * Holds the current value, default value, path, and metadata.
 * <p>
 * This class acts as a reference holder. The {@link #get()} method returns
 * the current cached value, which is updated when the configuration is reloaded.
 *
 * @param <T> The type of the configuration value (e.g., Integer, Boolean, String).
 * @author xI-Mx-Ix
 */
public class VxConfigValue<T> implements Supplier<T> {
    private final List<String> path;
    private final T defaultValue;
    private final String comment;
    private final Class<T> type;
    private volatile T value;

    /**
     * Constructs a new configuration value.
     *
     * @param path         The path segments to this value (e.g., ["general", "settings", "speed"]).
     * @param defaultValue The default value if none is found in the config.
     * @param comment      The comment describing this value.
     * @param type         The class type of the value for casting/verification.
     */
    protected VxConfigValue(List<String> path, T defaultValue, String comment, Class<T> type) {
        this.path = path;
        this.defaultValue = defaultValue;
        this.comment = comment;
        this.type = type;
        this.value = defaultValue;
    }

    /**
     * Retrieves the current value from the configuration.
     *
     * @return The cached configuration value.
     */
    @Override
    public T get() {
        return value;
    }

    /**
     * Updates the internal cache.
     * Intended to be called by the {@link VxConfigSpec} during the load process.
     *
     * @param newValue The new value loaded from disk.
     */
    void set(T newValue) {
        this.value = newValue;
    }

    /**
     * Returns the hierarchical path to this configuration value.
     *
     * @return A list of path segments.
     */
    public List<String> getPath() {
        return path;
    }

    /**
     * Returns the default value defined in the code.
     *
     * @return The default value.
     */
    public T getDefault() {
        return defaultValue;
    }

    /**
     * Returns the comment associated with this value.
     *
     * @return The comment string.
     */
    public String getComment() {
        return comment;
    }

    /**
     * Returns the class type of the value.
     *
     * @return The class object.
     */
    public Class<T> getType() {
        return type;
    }
}