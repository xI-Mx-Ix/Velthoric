/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.persistence.impl.constraint;

import com.github.stephengold.joltjni.enumerate.EConstraintSubType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import net.xmx.velthoric.core.constraint.VxConstraint;
import net.xmx.velthoric.core.persistence.schema.VxFieldType;
import net.xmx.velthoric.core.persistence.schema.VxSchema;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.network.VxByteBuf;

import java.util.UUID;

/**
 * A utility class for serializing and deserializing {@link VxConstraint} objects using a flexible TLV schema.
 * This codec handles the conversion between a constraint object and its byte representation,
 * focusing only on the data required to reconstruct the constraint, such as body IDs and settings.
 *
 * @author xI-Mx-Ix
 */
public final class VxConstraintCodec {

    /**
     * The schema definition for physics constraints.
     */
    public static final VxSchema<VxConstraint> SCHEMA = new VxSchema<>();

    static {
        // Field 1: Body 1 UUID
        SCHEMA.register((short) 1, "body1", VxFieldType.UUID,
                constraint -> constraint.getBody1Id() != null,
                (constraint, buf) -> buf.writeUUID(constraint.getBody1Id()),
                (constraint, buf) -> constraint.setBody1Id(buf.readUUID())
        );

        // Field 2: Body 2 UUID
        SCHEMA.register((short) 2, "body2", VxFieldType.UUID,
                constraint -> constraint.getBody2Id() != null,
                (constraint, buf) -> buf.writeUUID(constraint.getBody2Id()),
                (constraint, buf) -> constraint.setBody2Id(buf.readUUID())
        );

        // Field 3: SubType
        SCHEMA.register((short) 3, "subtype", VxFieldType.INT,
                constraint -> constraint.getSubType() != null,
                (constraint, buf) -> buf.writeInt(constraint.getSubType().ordinal()),
                (constraint, buf) -> constraint.setSubType(EConstraintSubType.values()[buf.readInt()])
        );

        // Field 4: Settings Data
        SCHEMA.register((short) 4, "settings", VxFieldType.BYTES,
                constraint -> constraint.getSettingsData() != null,
                (constraint, buf) -> buf.writeBytes(constraint.getSettingsData()),
                (constraint, buf) -> {
                    byte[] data = new byte[buf.readableBytes()];
                    buf.readBytes(data);
                    constraint.setSettingsData(data);
                }
        );
    }

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private VxConstraintCodec() {}

    /**
     * Serializes a {@link VxConstraint} into a buffer using the TLV schema.
     *
     * @param constraint The constraint object to serialize.
     * @param buf        The buffer to write the serialized data to.
     */
    public static void serialize(VxConstraint constraint, VxByteBuf buf) {
        ByteBuf tempBuf = ByteBufAllocator.DEFAULT.ioBuffer();
        try {
            VxByteBuf tempVxBuf = new VxByteBuf(tempBuf);
            SCHEMA.serialize(constraint, tempVxBuf);

            int length = tempBuf.readableBytes();
            if (length <= 2) return;

            buf.writeInt(length);
            buf.writeBytes(tempBuf);
        } finally {
            tempBuf.release();
        }
    }

    /**
     * Deserializes the constraint payload into a sliced buffer for deferred loading.
     *
     * @param constraintId The unique identifier for the constraint.
     * @param buf          The buffer to read from.
     * @return A {@link VxSerializedConstraintData} record.
     */
    public static VxSerializedConstraintData deserialize(UUID constraintId, VxByteBuf buf) {
        int length = buf.readInt();
        VxByteBuf payload = new VxByteBuf(buf.readBytes(length));

        return new VxSerializedConstraintData(constraintId, payload);
    }

    /**
     * Applies the internal state (bodies, settings) from the sliced payload buffer to the constraint.
     *
     * @param constraint The newly instantiated empty constraint.
     * @param buf        The sliced buffer containing the TLV schema data.
     */
    public static void readInternalPersistenceData(VxConstraint constraint, VxByteBuf buf) {
        try {
            SCHEMA.deserialize(constraint, buf);
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Failed to process schema for constraint {}: {}", constraint.getConstraintId(), e.getMessage());
        }
    }
}