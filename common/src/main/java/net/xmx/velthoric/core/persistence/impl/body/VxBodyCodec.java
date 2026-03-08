/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.persistence.impl.body;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.core.body.type.VxBody;
import org.jetbrains.annotations.Nullable;

import com.github.stephengold.joltjni.enumerate.EMotionType;
import net.xmx.velthoric.core.behavior.VxBehaviors;
import net.xmx.velthoric.core.body.server.VxServerBodyDataStore;

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
            writeInternalPersistenceData(body, tempVxBuf);

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
     * (the payload written by {@link VxBodyCodec#writeInternalPersistenceData(VxBody, VxByteBuf)}).
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

    /**
     * Serializes the internal state of the body for persistent storage.
     *
     * @param body The body to serialize.
     * @param buf  The buffer to write into.
     */
    public static void writeInternalPersistenceData(VxBody body, VxByteBuf buf) {
        if (body.getPhysicsWorld() == null || body.getDataStoreIndex() == -1) return;
        VxServerBodyDataStore store = body.getPhysicsWorld().getBodyManager().getDataStore();
        int idx = body.getDataStoreIndex();

        buf.writeDouble(store.posX[idx]);
        buf.writeDouble(store.posY[idx]);
        buf.writeDouble(store.posZ[idx]);
        buf.writeFloat(store.rotX[idx]);
        buf.writeFloat(store.rotY[idx]);
        buf.writeFloat(store.rotZ[idx]);
        buf.writeFloat(store.rotW[idx]);
        buf.writeFloat(store.velX[idx]);
        buf.writeFloat(store.velY[idx]);
        buf.writeFloat(store.velZ[idx]);
        buf.writeFloat(store.angVelX[idx]);
        buf.writeFloat(store.angVelY[idx]);
        buf.writeFloat(store.angVelZ[idx]);

        EMotionType motionType = store.motionType[idx];
        buf.writeByte(motionType != null ? motionType.ordinal() : EMotionType.Static.ordinal());

        if (VxBehaviors.SOFT_PHYSICS.isSet(store.behaviorBits[idx])) {
            float[] vertices = body.getPhysicsWorld().getBodyManager().retrieveSoftBodyVertices(body);
            if (vertices != null) {
                buf.writeInt(vertices.length);
                for (float val : vertices) buf.writeFloat(val);
            } else {
                buf.writeInt(0);
            }
        }

        body.getType().getPersistenceHandler().write(body, buf);
    }

    /**
     * Deserializes and restores the internal state of the body from storage.
     *
     * @param body The body to deserialize into.
     * @param buf  The buffer to read from.
     */
    public static void readInternalPersistenceData(VxBody body, VxByteBuf buf) {
        double px = buf.readDouble(), py = buf.readDouble(), pz = buf.readDouble();
        float rx = buf.readFloat(), ry = buf.readFloat(), rz = buf.readFloat(), rw = buf.readFloat();
        float vx = buf.readFloat(), vy = buf.readFloat(), vz = buf.readFloat();
        float avx = buf.readFloat(), avy = buf.readFloat(), avz = buf.readFloat();
        int motionOrdinal = buf.readByte();

        if (body.getPhysicsWorld() != null && body.getDataStoreIndex() != -1) {
            VxServerBodyDataStore store = body.getPhysicsWorld().getBodyManager().getDataStore();
            int idx = body.getDataStoreIndex();
            store.posX[idx] = px;
            store.posY[idx] = py;
            store.posZ[idx] = pz;
            store.rotX[idx] = rx;
            store.rotY[idx] = ry;
            store.rotZ[idx] = rz;
            store.rotW[idx] = rw;
            store.velX[idx] = vx;
            store.velY[idx] = vy;
            store.velZ[idx] = vz;
            store.angVelX[idx] = avx;
            store.angVelY[idx] = avy;
            store.angVelZ[idx] = avz;
            store.motionType[idx] = EMotionType.values()[Math.max(0, Math.min(motionOrdinal, EMotionType.values().length - 1))];
        }

        if (body.getType().isSoft()) {
            int vertexCount = buf.readInt();
            if (vertexCount > 0) {
                float[] vertices = new float[vertexCount];
                for (int i = 0; i < vertexCount; i++) vertices[i] = buf.readFloat();
                if (body.getPhysicsWorld() != null) {
                    body.getPhysicsWorld().getBodyManager().updateSoftBodyVertices(body, vertices);
                }
            }
        }

        body.getType().getPersistenceHandler().read(body, buf);
    }
}