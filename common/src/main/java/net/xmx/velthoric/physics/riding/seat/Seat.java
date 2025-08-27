package net.xmx.velthoric.physics.riding.seat;

import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

public class Seat {
    private final Properties properties;
    private boolean locked = false;

    public Seat(Properties properties) {
        this.properties = properties;
    }

    public String getName() {
        return this.properties.seatName;
    }

    public AABB getLocalAABB() {
        return this.properties.localAABB;
    }

    public Vector3f getRiderOffset() {
        return this.properties.riderOffset;
    }

    public boolean isLocked() {
        return locked;
    }

    public void lock() {
        this.locked = true;
    }

    public void unlock() {
        this.locked = false;
    }

    public static class Properties {
        public final String seatName;
        public final AABB localAABB;
        public final Vector3f riderOffset;

        public Properties(String seatName, AABB localAABB, Vector3f riderOffset) {
            this.seatName = seatName;
            this.localAABB = localAABB;
            this.riderOffset = riderOffset;
        }
    }
}