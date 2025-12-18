/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.physics.body.sync.accessor;

import net.timtaran.interactivemc.physics.physics.body.sync.VxDataSerializer;
import net.timtaran.interactivemc.physics.physics.body.sync.VxSyncMode;

/**
 * A data accessor specifically for data where the CLIENT has authority.
 * <p>
 * - Only the Client can set this data via {@code body.setClientData()}.
 * - The Server receives updates and validates them.
 * - Updates propagate Client -> Server.
 *
 * @param <T> The type of data.
 * @author xI-Mx-Ix
 */
public final class VxClientAccessor<T> extends VxDataAccessor<T> {

    private VxClientAccessor(int id, VxDataSerializer<T> serializer) {
        super(id, serializer);
    }

    /**
     * Creates a new Client-Authoritative Data Accessor.
     *
     * @param bodyClass  The class of the body this data belongs to.
     * @param serializer The serializer for the data type.
     * @param <T>        The data type.
     * @return A new typed accessor.
     */
    public static <T> VxClientAccessor<T> create(Class<?> bodyClass, VxDataSerializer<T> serializer) {
        return new VxClientAccessor<>(generateId(bodyClass), serializer);
    }

    @Override
    public VxSyncMode getMode() {
        return VxSyncMode.CLIENT;
    }
}