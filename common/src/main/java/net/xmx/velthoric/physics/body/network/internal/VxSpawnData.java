/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.network.internal;

import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.body.client.VxClientBodyManager;
import net.xmx.velthoric.physics.body.type.VxBody;

import java.util.UUID;

/**
 * A utility class providing static helpers for high-performance serialization of spawn data.
 * <p>
 * Unlike traditional DTOs, this class does not hold state, but rather provides logic to write
 * body information directly into a shared binary stream, avoiding object creation.
 *
 * @author xI-Mx-Ix
 */
public class VxSpawnData {

    /**
     * Serializes a physics body's complete identity and initial state into the buffer.
     *
     * @param buf       The destination buffer.
     * @param body      The body to serialize.
     * @param timestamp Current simulation timestamp.
     */
    public static void writeRaw(VxByteBuf buf, VxBody body, long timestamp) {
        buf.writeUUID(body.getPhysicsId());
        buf.writeVarInt(body.getNetworkId());
        buf.writeResourceLocation(body.getType().getTypeId());
        buf.writeLong(timestamp);
        body.getTransform().toBuffer(buf);
        body.writeInitialSyncData(buf);
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
    }
}