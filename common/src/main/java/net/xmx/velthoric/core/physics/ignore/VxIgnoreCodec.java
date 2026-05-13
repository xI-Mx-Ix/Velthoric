/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.physics.ignore;

import net.xmx.velthoric.network.VxByteBuf;

import java.util.UUID;

/**
 * Serializer for {@link VxBodyPairIgnore} objects.
 *
 * @author xI-Mx-Ix
 */
public final class VxIgnoreCodec {

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private VxIgnoreCodec() {}

    /**
     * Serializes a collision ignore pair into the provided byte buffer.
     * <p>
     * The serialization format consists of two consecutive UUIDs representing
     * the participating bodies.
     *
     * @param ignore The ignore pair to serialize.
     * @param buf    The buffer to write data into.
     */
    public static void serialize(VxBodyPairIgnore ignore, VxByteBuf buf) {
        buf.writeUUID(ignore.getBody1Id());
        buf.writeUUID(ignore.getBody2Id());
    }

    /**
     * Reconstitutes a collision ignore pair from the provided byte buffer.
     *
     * @param buf The buffer containing the serialized UUID data.
     * @return A new {@link VxBodyPairIgnore} instance.
     */
    public static VxBodyPairIgnore deserialize(VxByteBuf buf) {
        UUID id1 = buf.readUUID();
        UUID id2 = buf.readUUID();
        return new VxBodyPairIgnore(id1, id2);
    }
}