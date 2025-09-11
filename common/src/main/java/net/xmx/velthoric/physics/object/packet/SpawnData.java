/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.packet;

import com.github.stephengold.joltjni.enumerate.EBodyType;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.object.VxAbstractBody;
import net.xmx.velthoric.physics.object.type.VxSoftBody;

import java.util.UUID;

/**
 * A data-transfer object that encapsulates all the information needed to spawn
 * a physics object on the client. This is used in spawn packets.
 *
 * @author xI-Mx-Ix
 */
public class SpawnData {
    /** The unique identifier of the object. */
    public final UUID id;
    /** The resource location identifying the object's type. */
    public final ResourceLocation typeIdentifier;
    /** The type of the physics body (e.g., Rigid or Soft). */
    public final EBodyType objectType;
    /** The server-side timestamp when the object was spawned. */
    public final long timestamp;
    /** A raw byte array containing the initial transform and any custom creation data. */
    public final byte[] data;

    /**
     * Constructs spawn data from a server-side physics object.
     *
     * @param obj       The object to create spawn data for.
     * @param timestamp The server-side timestamp of the spawn event.
     */
    public SpawnData(VxAbstractBody obj, long timestamp) {
        this.id = obj.getPhysicsId();
        this.typeIdentifier = obj.getType().getTypeId();
        this.objectType = obj instanceof VxSoftBody ? EBodyType.SoftBody : EBodyType.RigidBody;
        this.timestamp = timestamp;

        // Serialize the initial transform and custom data into the byte array.
        VxByteBuf buf = new VxByteBuf(Unpooled.buffer());
        try {
            obj.getGameTransform().toBuffer(buf);
            obj.writeCreationData(buf);
            this.data = new byte[buf.readableBytes()];
            buf.readBytes(this.data);
        } finally {
            if(buf.refCnt() > 0) {
                buf.release();
            }
        }
    }

    /**
     * Constructs spawn data by decoding it from a network buffer.
     *
     * @param buf The buffer to read from.
     */
    public SpawnData(FriendlyByteBuf buf) {
        this.id = buf.readUUID();
        this.typeIdentifier = buf.readResourceLocation();
        this.objectType = buf.readEnum(EBodyType.class);
        this.timestamp = buf.readLong();
        this.data = buf.readByteArray();
    }

    /**
     * Encodes the spawn data into a network buffer for sending.
     *
     * @param buf The buffer to write to.
     */
    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeResourceLocation(typeIdentifier);
        buf.writeEnum(objectType);
        buf.writeLong(timestamp);
        buf.writeByteArray(data);
    }

    /**
     * Estimates the size of the encoded data in bytes. This is used for packet batching
     * to avoid exceeding payload size limits.
     *
     * @return The estimated size in bytes.
     */
    public int estimateSize() {
        String typeStr = typeIdentifier.toString();
        // UUID (16) + Type ID (varint len + string) + Enum (4) + Timestamp (8) + Data (varint len + byte array)
        return 16 + FriendlyByteBuf.getVarIntSize(typeStr.length()) + typeStr.length() + 4 + 8 + FriendlyByteBuf.getVarIntSize(data.length) + data.length;
    }
}