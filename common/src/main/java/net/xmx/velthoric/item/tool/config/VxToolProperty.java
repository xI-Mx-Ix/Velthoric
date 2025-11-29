/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.tool.config;

import org.jetbrains.annotations.Nullable;

/**
 * Represents a single configuration property with a specific type and optional bounds.
 *
 * @param <T> The type of the value (e.g., Integer, Boolean, String).
 * @author xI-Mx-Ix
 */
public class VxToolProperty<T> {
    private final String name;
    private T value;
    private final Number min;
    private final Number max;
    private final Class<T> type;

    /**
     * Creates a new property.
     *
     * @param name  The display name or key of the property.
     * @param value The initial/default value.
     * @param min   The minimum allowed value (nullable for non-numeric types).
     * @param max   The maximum allowed value (nullable for non-numeric types).
     * @param type  The class type of the value.
     */
    public VxToolProperty(String name, T value, @Nullable Number min, @Nullable Number max, Class<T> type) {
        this.name = name;
        this.value = value;
        this.min = min;
        this.max = max;
        this.type = type;
    }

    /**
     * Gets the name of the property.
     *
     * @return The property name.
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the current value.
     *
     * @return The value.
     */
    public T getValue() {
        return value;
    }

    /**
     * Sets the value of the property.
     * <p>
     * Unchecked cast is suppressed as the VxToolConfig manager ensures
     * type safety via correct setter usage.
     *
     * @param value The new value to set.
     */
    @SuppressWarnings("unchecked")
    public void setValue(Object value) {
        // Ideally, validation logic for min/max would go here if needed.
        this.value = (T) value;
    }

    /**
     * Gets the minimum allowed value, if applicable.
     *
     * @return The minimum value as a Number, or null.
     */
    @Nullable
    public Number getMin() {
        return min;
    }

    /**
     * Gets the maximum allowed value, if applicable.
     *
     * @return The maximum value as a Number, or null.
     */
    @Nullable
    public Number getMax() {
        return max;
    }

    /**
     * Gets the class type of the property value.
     *
     * @return The class type.
     */
    public Class<T> getType() {
        return type;
    }
}