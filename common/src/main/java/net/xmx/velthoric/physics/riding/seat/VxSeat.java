/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.riding.seat;

import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

public class VxSeat {
    private final String seatName;
    private final AABB localAABB;
    private final Vector3f riderOffset;
    private final boolean isDriverSeat;

    public VxSeat(String seatName, AABB localAABB, Vector3f riderOffset, boolean isDriverSeat) {
        this.seatName = seatName;
        this.localAABB = localAABB;
        this.riderOffset = riderOffset;
        this.isDriverSeat = isDriverSeat;
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