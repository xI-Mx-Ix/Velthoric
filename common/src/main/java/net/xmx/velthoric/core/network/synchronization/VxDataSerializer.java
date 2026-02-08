/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.network.synchronization;

import net.xmx.velthoric.network.VxByteBuf;

/**
 * An interface for serializing and deserializing a specific data type to/from a buffer.
 * This is a key component of the synchronized data system.
 *
 * @param <T> The type of data to be handled.
 * @author xI-Mx-Ix
 */
public interface VxDataSerializer<T> {
    /**
     * Writes a value to the buffer.
     * @param buf The buffer to write to.
     * @param value The value to write.
     */
    void write(VxByteBuf buf, T value);

    /**
     * Reads a value from the buffer.
     * @param buf The buffer to read from.
     * @return The read value.
     */
    T read(VxByteBuf buf);

    /**
     * Creates a copy of the given value. For immutable types, this can just return the original value.
     * For mutable types, a new instance should be created.
     * @param value The value to copy.
     * @return A copy of the value.
     */
    T copy(T value);
}