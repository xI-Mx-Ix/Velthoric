/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.network.synchronization.accessor;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.xmx.velthoric.core.network.synchronization.VxDataSerializer;
import net.xmx.velthoric.core.network.synchronization.VxSyncMode;

import java.util.Objects;

/**
 * Base class for a type-safe key used to access synchronized data.
 * This class is abstract to enforce the usage of specific subclasses
 * for Server vs. Client authority, ensuring type safety at compile time.
 *
 * @param <T> The type of the data this accessor points to.
 * @author xI-Mx-Ix
 */
public abstract class VxDataAccessor<T> {
    
    // Shared ID pool ensures unique IDs across both server and client accessors
    private static final Object2IntMap<Class<?>> ID_POOL = new Object2IntOpenHashMap<>();
    private static final int MAX_ID_VALUE = 254;

    private final int id;
    private final VxDataSerializer<T> serializer;

    protected VxDataAccessor(int id, VxDataSerializer<T> serializer) {
        this.id = id;
        this.serializer = serializer;
    }

    /**
     * Generates a unique ID for a new accessor based on the body class hierarchy.
     * Use this helper in subclass factory methods.
     * 
     * @param bodyClass The class to generate the ID for.
     * @return A unique ID.
     */
    protected static int generateId(Class<?> bodyClass) {
        int nextId;
        if (ID_POOL.containsKey(bodyClass)) {
            nextId = ID_POOL.getInt(bodyClass) + 1;
        } else {
            int baseId = 0;
            Class<?> currentClass = bodyClass;
            while (currentClass.getSuperclass() != null) {
                currentClass = currentClass.getSuperclass();
                if (ID_POOL.containsKey(currentClass)) {
                    baseId = ID_POOL.getInt(currentClass) + 1;
                    break;
                }
            }
            nextId = baseId;
        }

        if (nextId > MAX_ID_VALUE) {
            throw new IllegalArgumentException("Data value id is too big with " + nextId + "! (Max is " + MAX_ID_VALUE + ")");
        }

        ID_POOL.put(bodyClass, nextId);
        return nextId;
    }

    /**
     * @return The unique ID of this accessor.
     */
    public int getId() {
        return id;
    }

    /**
     * @return The serializer used to read/write this data.
     */
    public VxDataSerializer<T> getSerializer() {
        return serializer;
    }

    /**
     * Returns the authority mode of this accessor.
     * Subclasses define whether they are SERVER or CLIENT authoritative.
     * 
     * @return The sync mode.
     */
    public abstract VxSyncMode getMode();

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