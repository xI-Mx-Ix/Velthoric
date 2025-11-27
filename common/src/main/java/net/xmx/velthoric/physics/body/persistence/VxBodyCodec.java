/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.persistence;

import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.body.type.VxBody;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A utility class for serializing and deserializing {@link VxBody} objects.
 * This codec translates the state of a physics body (including transform, velocity, and
 * custom data) into a byte representation for storage, and vice versa. Deserialization
 * produces a {@link VxSerializedBodyData} record, which acts as an intermediary
 * data-transfer object for reconstructing the body.
 *
 * @author xI-Mx-Ix
 */
public final class VxBodyCodec {

    private VxBodyCodec() {}

    /**
     * Serializes a {@link VxBody} and its current physics state into a buffer.
     * It writes the body's unique ID and type, then delegates to the body to write its
     * internal persistence data (transform, velocities, vertices, etc.).
     *
     * @param body      The physics body to serialize.
     * @param buf       The buffer to write the serialized data into.
     */
    public static void serialize(VxBody body, VxByteBuf buf) {
        buf.writeUUID(body.getPhysicsId());
        buf.writeUtf(body.getType().getTypeId().toString());

        // Delegate the writing of the actual physics and user data to the body itself.
        // This ensures subclasses like VxSoftBody can save their specific vertex data.
        body.writeInternalPersistenceData(buf);
    }

    /**
     * Deserializes physics body data from a buffer into a {@link VxSerializedBodyData} record.
     * This record contains the ID, Type, and a buffer slice of the remaining data
     * (the payload written by {@link VxBody#writeInternalPersistenceData(VxByteBuf)}).
     *
     * @param buf The buffer to read the serialized data from.
     * @return A {@link VxSerializedBodyData} record, or null if deserialization fails.
     */
    @Nullable
    public static VxSerializedBodyData deserialize(VxByteBuf buf) {
        try {
            UUID id = buf.readUUID();
            ResourceLocation typeId = ResourceLocation.tryParse(buf.readUtf());

            // The rest of the buffer is the body's internal data (Transform, Velocity, Vertices, User Data).
            // We copy this into a new buffer slice to be handed to the body instance later.
            VxByteBuf bodyData = new VxByteBuf(buf.readBytes(buf.readableBytes()));

            return new VxSerializedBodyData(typeId, id, bodyData);
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Failed to deserialize physics body from data", e);
            return null;
        }
    }
}