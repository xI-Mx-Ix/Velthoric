/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.network.internal;

import io.netty.buffer.ByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.core.body.client.VxClientBodyManager;
import net.xmx.velthoric.core.body.VxBody;
import net.xmx.velthoric.core.body.shape.VxCollisionShape;
import net.xmx.velthoric.core.body.shape.VxShapeCodec;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * A utility class providing static helpers for high-performance serialization of spawn data.
 * <p>
 * This class now supports direct writing to {@link ByteBuf} to facilitate the
 * zero-allocation pipeline used by the Network Dispatcher.
 *
 * @author xI-Mx-Ix
 */
public class VxSpawnData {

    /**
     * Serializes a physics body's complete identity and initial state into the buffer.
     * Uses direct primitives to avoid object creation.
     *
     * @param buf       The destination Netty ByteBuf.
     * @param body      The body to serialize.
     * @param timestamp Current simulation timestamp.
     */
    public static void writeRaw(ByteBuf buf, VxBody body, long timestamp) {
        // Manually write UUID (High/Low) to avoid FriendlyByteBuf overhead
        UUID id = body.getPhysicsId();
        buf.writeLong(id.getMostSignificantBits());
        buf.writeLong(id.getLeastSignificantBits());

        // Network ID (VarInt for space efficiency)
        writeVarInt(buf, body.getNetworkId());

        // Resource Location (String)
        String typeId = body.getType().getTypeId().toString();
        writeUtf(buf, typeId);

        buf.writeLong(timestamp);

        // Wrap for complex objects that rely on VxByteBuf API
        // NOTE: This wrapper does NOT copy data, it just delegates to the ByteBuf.
        VxByteBuf wrapper = new VxByteBuf(buf);
        body.getTransform().toBuffer(wrapper);
        body.writeInitialSyncData(wrapper);

        // Write collision shape (nullable)
        VxCollisionShape shape = body.getShape();
        if (shape != null) {
            buf.writeBoolean(true);
            VxShapeCodec.write(wrapper, shape);
        } else {
            buf.writeBoolean(false);
        }
    }

    /**
     * Deserializes body information from a buffer and triggers a client-side spawn.
     *
     * @param buf     The source buffer.
     * @param manager The client-side manager to handle the instantiation.
     */
    public static void readAndSpawn(VxByteBuf buf, VxClientBodyManager manager) {
        UUID id = buf.readUUID();
        int netId = buf.readVarInt();
        ResourceLocation type = buf.readResourceLocation();
        long timestamp = buf.readLong();
        manager.spawnBody(id, netId, type, buf, timestamp);

        // Read and apply shape after spawn (body is now registered in manager)
        if (buf.readBoolean()) {
            VxCollisionShape shape = VxShapeCodec.read(buf);
            VxBody body = manager.getBody(id);
            if (body != null) {
                body.setShape(shape);
            }
        }
    }

    // --- Primitives Helpers for Raw ByteBuf (Netty doesn't have native VarInt write) ---

    /**
     * Helper to write a VarInt to a raw ByteBuf.
     */
    private static void writeVarInt(ByteBuf buf, int value) {
        while ((value & -128) != 0) {
            buf.writeByte(value & 127 | 128);
            value >>>= 7;
        }
        buf.writeByte(value);
    }

    /**
     * Helper to write a UTF-8 string to a raw ByteBuf (Length VarInt + Bytes).
     */
    private static void writeUtf(ByteBuf buf, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(buf, bytes.length);
        buf.writeBytes(bytes);
    }
}