/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.network.internal;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.body.type.VxBody;

import java.util.UUID;

/**
 * A data transfer object that encapsulates all necessary information to spawn a physics body on the client.
 * This includes the body's identity, type, initial state, and custom creation data.
 *
 * @author xI-Mx-Ix
 */
public class VxSpawnData {
    public final UUID id;
    public final int networkId;
    public final ResourceLocation typeIdentifier;
    public final long timestamp;
    public final byte[] data;

    /**
     * Constructs spawn data from a server-side {@link VxBody}.
     * This serializes the body's initial transform and custom sync data.
     *
     * @param obj       The physics body to create spawn data for.
     * @param timestamp The server-side timestamp of the spawn event.
     */
    public VxSpawnData(VxBody obj, long timestamp) {
        this.id = obj.getPhysicsId();
        this.networkId = obj.getNetworkId();
        this.typeIdentifier = obj.getType().getTypeId();
        this.timestamp = timestamp;

        // Serialize the transform and custom sync data into a single byte array.
        VxByteBuf buf = new VxByteBuf(Unpooled.buffer());
        try {
            obj.getTransform().toBuffer(buf);
            obj.writeInitialSyncData(buf);

            this.data = new byte[buf.readableBytes()];
            buf.readBytes(this.data);
        } finally {
            if (buf.refCnt() > 0) {
                buf.release();
            }
        }
    }

    /**
     * Constructs spawn data by deserializing it from a network buffer.
     *
     * @param buf The buffer to read from.
     */
    public VxSpawnData(FriendlyByteBuf buf) {
        this.id = buf.readUUID();
        this.networkId = buf.readVarInt();
        this.typeIdentifier = buf.readResourceLocation();
        this.timestamp = buf.readLong();
        this.data = buf.readByteArray();
    }

    /**
     * Serializes this spawn data into a network buffer.
     *
     * @param buf The buffer to write to.
     */
    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeVarInt(networkId);
        buf.writeResourceLocation(typeIdentifier);
        buf.writeLong(timestamp);
        buf.writeByteArray(data);
    }

    /**
     * Estimates the size of this spawn data in bytes for network packet batching.
     *
     * @return The estimated size in bytes.
     */
    public int estimateSize() {
        String typeStr = typeIdentifier.toString();
        // Calculate size: UUID (16) + NetworkID (varint) + RL (varint + string) + Timestamp (8) + Data (varint + bytes)
        return 16 + VxByteBuf.varIntSize(networkId) + VxByteBuf.varIntSize(typeStr.length()) + typeStr.length() + 8 + VxByteBuf.varIntSize(data.length) + data.length;
    }
}