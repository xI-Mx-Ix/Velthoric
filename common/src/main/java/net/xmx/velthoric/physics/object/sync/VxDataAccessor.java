/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.sync;

import java.util.Objects;

/**
 * A type-safe key used to access a piece of synchronized data on a physics object.
 * It holds a unique ID and the serializer for its data type.
 *
 * @param <T> The type of the data this accessor points to.
 * @author xI-Mx-Ix
 */
public class VxDataAccessor<T> {
    private final int id;
    private final VxDataSerializer<T> serializer;

    public VxDataAccessor(int id, VxDataSerializer<T> serializer) {
        this.id = id;
        this.serializer = serializer;
    }

    public int getId() {
        return id;
    }

    public VxDataSerializer<T> getSerializer() {
        return serializer;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VxDataAccessor<?> that = (VxDataAccessor<?>) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}