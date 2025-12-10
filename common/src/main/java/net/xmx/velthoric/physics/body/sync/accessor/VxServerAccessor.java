/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.sync.accessor;

import net.xmx.velthoric.physics.body.sync.VxDataSerializer;
import net.xmx.velthoric.physics.body.sync.VxSyncMode;

/**
 * A data accessor specifically for data where the SERVER has authority.
 * <p>
 * - Only the Server can set this data via {@code body.setServerData()}.
 * - The Client can only read this data.
 * - Updates propagate Server -> Client.
 *
 * @param <T> The type of data.
 * @author xI-Mx-Ix
 */
public final class VxServerAccessor<T> extends VxDataAccessor<T> {

    private VxServerAccessor(int id, VxDataSerializer<T> serializer) {
        super(id, serializer);
    }

    /**
     * Creates a new Server-Authoritative Data Accessor.
     *
     * @param bodyClass  The class of the body this data belongs to.
     * @param serializer The serializer for the data type.
     * @param <T>        The data type.
     * @return A new typed accessor.
     */
    public static <T> VxServerAccessor<T> create(Class<?> bodyClass, VxDataSerializer<T> serializer) {
        return new VxServerAccessor<>(generateId(bodyClass), serializer);
    }

    @Override
    public VxSyncMode getMode() {
        return VxSyncMode.SERVER;
    }
}