/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.tool.config;

/**
 * A single configuration property.
 *
 * @param <T> The type of the value.
 * @author xI-Mx-Ix
 */
public class VxToolProperty<T> {
    private final String name;
    private T value;
    private final Number min;
    private final Number max;
    private final Class<T> type;

    public VxToolProperty(String name, T value, Number min, Number max, Class<T> type) {
        this.name = name;
        this.value = value;
        this.min = min;
        this.max = max;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public T getValue() {
        return value;
    }
    
    @SuppressWarnings("unchecked")
    public void setValue(Object value) {
        this.value = (T) value;
    }
    
    public Number getMin() {
        return min;
    }

    public Number getMax() {
        return max;
    }

    public Class<T> getType() {
        return type;
    }
}