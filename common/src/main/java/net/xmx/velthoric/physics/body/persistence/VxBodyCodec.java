/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.persistence;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
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
     * <p>
     * <b>Format:</b>
     * <ul>
     *     <li>UUID (16 bytes)</li>
     *     <li>Type ID (UTF String)</li>
     *     <li>Data Length (4 bytes)</li>
     *     <li>Body Data (Variable payload)</li>
     * </ul>
     *
     * @param body      The physics body to serialize.
     * @param buf       The buffer to write the serialized data into.
     */
    public static void serialize(VxBody body, VxByteBuf buf) {
        buf.writeUUID(body.getPhysicsId());
        buf.writeUtf(body.getType().getTypeId().toString());

        // We use a temporary buffer to capture the variable-length internal data.
        // This ensures we can calculate the exact size and prefix it, allowing the
        // deserializer to distinguish where this body ends and the next one begins.
        ByteBuf tempBuf = ByteBufAllocator.DEFAULT.ioBuffer();
        try {
            VxByteBuf tempVxBuf = new VxByteBuf(tempBuf);
            body.writeInternalPersistenceData(tempVxBuf);

            int length = tempBuf.readableBytes();

            // Write length prefix
            buf.writeInt(length);
            // Write the actual payload
            buf.writeBytes(tempBuf);
        } finally {
            tempBuf.release();
        }
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

            // Read the length of the internal data block
            int dataLength = buf.readInt();

            // Safety check to prevent reading past buffer bounds
            if (buf.readableBytes() < dataLength) {
                VxMainClass.LOGGER.error("Malformed body data for ID {}: expected {} bytes, but only {} remain.", id, dataLength, buf.readableBytes());
                return null;
            }

            // Slice exactly the amount of data belonging to this body.
            // readBytes() creates a slice and advances the reader index of 'buf' by 'dataLength'.
            // This leaves the buffer positioned correctly for the next body in the chunk.
            VxByteBuf bodyData = new VxByteBuf(buf.readBytes(dataLength));

            return new VxSerializedBodyData(typeId, id, bodyData);
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Failed to deserialize physics body from data", e);
            return null;
        }
    }
}