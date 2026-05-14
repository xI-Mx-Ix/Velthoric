/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.physics.ignore.persistence;

import net.xmx.velthoric.core.persistence.schema.VxFieldType;
import net.xmx.velthoric.core.persistence.schema.VxSchema;
import net.xmx.velthoric.core.physics.ignore.VxBodyPairIgnore;
import net.xmx.velthoric.network.VxByteBuf;

import java.util.UUID;

/**
 * Serializer for {@link VxBodyPairIgnore} objects using a flexible TLV schema.
 *
 * @author xI-Mx-Ix
 */
public final class VxIgnoreCodec {

    /**
     * The schema definition for ignore pairs.
     */
    public static final VxSchema<VxBodyPairIgnore> SCHEMA = new VxSchema<>();

    static {
        // Field 1: Body 1 UUID
        SCHEMA.register((short) 1, "body1", VxFieldType.UUID,
            ignore -> ignore.getBody1Id() != null,
            (ignore, buf) -> buf.writeUUID(ignore.getBody1Id()),
            (ignore, buf) -> {} // Immutable object, reader handles creation manually
        );

        // Field 2: Body 2 UUID
        SCHEMA.register((short) 2, "body2", VxFieldType.UUID,
            ignore -> ignore.getBody2Id() != null,
            (ignore, buf) -> buf.writeUUID(ignore.getBody2Id()),
            (ignore, buf) -> {} // Immutable object, reader handles creation manually
        );
    }

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private VxIgnoreCodec() {}

    /**
     * Serializes a collision ignore pair into the provided byte buffer using the TLV schema.
     *
     * @param ignore The ignore pair to serialize.
     * @param buf    The buffer to write data into.
     */
    public static void serialize(VxBodyPairIgnore ignore, VxByteBuf buf) {
        SCHEMA.serialize(ignore, buf);
    }

    /**
     * Reconstitutes a collision ignore pair from the provided byte buffer using the TLV schema.
     *
     * @param buf The buffer containing the serialized UUID data.
     * @return A new {@link VxBodyPairIgnore} instance.
     */
    public static VxBodyPairIgnore deserialize(VxByteBuf buf) {
        UUID id1 = null;
        UUID id2 = null;

        while (buf.isReadable()) {
            short id = buf.readShort();
            if (id == VxSchema.END_OF_SCHEMA) break;

            byte typeId = buf.readByte();
            VxFieldType type = VxFieldType.fromId(typeId);

            if (type == null) {
                return null; // Corrupt data
            }

            int length = -1;
            if (type.isVariableLength()) {
                length = buf.readInt();
            }

            if (id == 1 && type == VxFieldType.UUID) {
                id1 = buf.readUUID();
            } else if (id == 2 && type == VxFieldType.UUID) {
                id2 = buf.readUUID();
            } else {
                if (type.isVariableLength()) {
                    buf.skipBytes(length);
                } else {
                    buf.skipBytes(type.getFixedLength());
                }
            }
        }

        if (id1 == null || id2 == null) return null;
        return new VxBodyPairIgnore(id1, id2);
    }
}