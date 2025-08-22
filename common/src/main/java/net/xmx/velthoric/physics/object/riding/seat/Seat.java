package net.xmx.velthoric.physics.object.riding.seat;

import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

public class Seat {
    private final String seatName;
    private final AABB localAABB;
    private final Vector3f riderOffset;

    public Seat(String seatName, AABB localAABB, Vector3f riderOffset) {
        this.seatName = seatName;
        this.localAABB = localAABB;
        this.riderOffset = riderOffset;
    }

    public String getSeatName() {
        return seatName;
    }

    public AABB getLocalAABB() {
        return localAABB;
    }

    public Vector3f getRiderOffset() {
        return riderOffset;
    }
}