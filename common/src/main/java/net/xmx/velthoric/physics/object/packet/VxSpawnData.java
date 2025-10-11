/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.packet;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.mounting.VxMountable;
import net.xmx.velthoric.physics.mounting.manager.VxMountingManager;
import net.xmx.velthoric.physics.mounting.seat.VxSeat;
import net.xmx.velthoric.physics.object.type.VxBody;

import java.util.Collection;
import java.util.UUID;

/**
 * A data transfer object that encapsulates all necessary information to spawn a physics object on the client.
 * This includes the object's identity, type, initial state, custom creation data, and any associated seats.
 *
 * @author xI-Mx-Ix
 */
public class VxSpawnData {
    public final UUID id;
    public final ResourceLocation typeIdentifier;
    public final long timestamp;
    public final byte[] data;

    /**
     * Constructs spawn data from a server-side {@link VxBody}.
     * This serializes the object's initial transform, custom sync data, and seat information.
     *
     * @param obj       The physics object to create spawn data for.
     * @param timestamp The server-side timestamp of the spawn event.
     */
    public VxSpawnData(VxBody obj, long timestamp) {
        this.id = obj.getPhysicsId();
        this.typeIdentifier = obj.getType().getTypeId();
        this.timestamp = timestamp;

        // Serialize the transform, custom sync data, and seat data into a single byte array.
        VxByteBuf buf = new VxByteBuf(Unpooled.buffer());
        try {
            obj.getTransform().toBuffer(buf);
            obj.writeInitialSyncData(buf);

            if (obj instanceof VxMountable) {
                VxMountingManager ridingManager = obj.getPhysicsWorld().getMountingManager();
                Collection<VxSeat> seats = ridingManager.getSeats(obj.getPhysicsId());
                buf.writeVarInt(seats.size());
                for (VxSeat seat : seats) {
                    seat.encode(buf);
                }
            } else {
                buf.writeVarInt(0); // No seats
            }

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
        // Calculate size: UUID (16) + RL (varint + string) + Timestamp (8) + Data (varint + bytes)
        return 16 + FriendlyByteBuf.getVarIntSize(typeStr.length()) + typeStr.length() + 8 + FriendlyByteBuf.getVarIntSize(data.length) + data.length;
    }
}