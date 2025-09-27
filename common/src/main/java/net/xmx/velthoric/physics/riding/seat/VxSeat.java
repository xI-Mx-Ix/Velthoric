/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.riding.seat;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/**
 * Represents a single seat on a rideable physics object.
 * A seat defines a location where a player can sit, including its collision box,
 * rider offset, and whether it's a driver seat.
 *
 * @author xI-Mx-Ix
 */
public class VxSeat {
    private final String seatName;
    private final AABB localAABB;
    private final Vector3f riderOffset;
    private final boolean isDriverSeat;

    /**
     * Constructs a new seat.
     *
     * @param seatName     The unique name of the seat.
     * @param localAABB    The local-space bounding box for interaction.
     * @param riderOffset  The local-space offset from the object's origin where the rider is positioned.
     * @param isDriverSeat True if this seat allows the rider to control the object.
     */
    public VxSeat(String seatName, AABB localAABB, Vector3f riderOffset, boolean isDriverSeat) {
        this.seatName = seatName;
        this.localAABB = localAABB;
        this.riderOffset = riderOffset;
        this.isDriverSeat = isDriverSeat;
    }

    /**
     * Constructs a seat by deserializing it from a network buffer.
     *
     * @param buf The buffer to read from.
     */
    public VxSeat(FriendlyByteBuf buf) {
        this.seatName = buf.readUtf();
        double minX = buf.readDouble();
        double minY = buf.readDouble();
        double minZ = buf.readDouble();
        double maxX = buf.readDouble();
        double maxY = buf.readDouble();
        double maxZ = buf.readDouble();
        this.localAABB = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        this.riderOffset = new Vector3f(buf.readFloat(), buf.readFloat(), buf.readFloat());
        this.isDriverSeat = buf.readBoolean();
    }

    /**
     * Serializes this seat's data into a network buffer.
     *
     * @param buf The buffer to write to.
     */
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.seatName);
        buf.writeDouble(this.localAABB.minX);
        buf.writeDouble(this.localAABB.minY);
        buf.writeDouble(this.localAABB.minZ);
        buf.writeDouble(this.localAABB.maxX);
        buf.writeDouble(this.localAABB.maxY);
        buf.writeDouble(this.localAABB.maxZ);
        buf.writeFloat(this.riderOffset.x());
        buf.writeFloat(this.riderOffset.y());
        buf.writeFloat(this.riderOffset.z());
        buf.writeBoolean(this.isDriverSeat);
    }

    public String getName() {
        return this.seatName;
    }

    public AABB getLocalAABB() {
        return this.localAABB;
    }

    public Vector3f getRiderOffset() {
        return this.riderOffset;
    }

    public boolean isDriverSeat() {
        return this.isDriverSeat;
    }
}