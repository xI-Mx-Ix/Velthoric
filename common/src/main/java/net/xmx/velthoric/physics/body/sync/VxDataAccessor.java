/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.sync;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Objects;

/**
 * A type-safe key used to access a piece of synchronized data on a physics body.
 * It holds a unique ID and the serializer for its data type.
 * This class now manages ID creation in a deterministic way, similar to Minecraft's SynchedEntityData,
 * to ensure client and server IDs always match.
 *
 * @param <T> The type of the data this accessor points to.
 * @author xI-Mx-Ix
 */
public class VxDataAccessor<T> {
    // This map stores the last used ID for each VxBody class, ensuring IDs are unique and deterministic.
    private static final Object2IntMap<Class<?>> ID_POOL = new Object2IntOpenHashMap<>();
    private static final int MAX_ID_VALUE = 254;

    private final int id;
    private final VxDataSerializer<T> serializer;

    private VxDataAccessor(int id, VxDataSerializer<T> serializer) {
        this.id = id;
        this.serializer = serializer;
    }

    /**
     * Creates a new Data Accessor with a unique, deterministic ID for a specific body class.
     * This method must be called from the static initializer of a body class.
     *
     * @param bodyClass The class of the VxBody (or related client/server class) this accessor belongs to.
     *                  This is used to generate a consistent ID.
     * @param serializer The serializer for the data type.
     * @return A new {@link VxDataAccessor}.
     */
    public static <T> VxDataAccessor<T> create(Class<?> bodyClass, VxDataSerializer<T> serializer) {
        int nextId;
        // If the class is already in our pool, we take the next available ID.
        if (ID_POOL.containsKey(bodyClass)) {
            nextId = ID_POOL.getInt(bodyClass) + 1;
        } else {
            // If not, we find the highest ID from its parent classes to ensure no overlaps.
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

        // Store the new highest ID for this class.
        ID_POOL.put(bodyClass, nextId);
        return new VxDataAccessor<>(nextId, serializer);
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