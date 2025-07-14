package net.xmx.xbullet.physics.object.raycast.info;

import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.operator.Op;

public final class PhysicsHitInfo {
    private final int bodyId;
    private final float hitFraction;
    private final Vec3 hitNormal;

    public PhysicsHitInfo(int bodyId, float hitFraction, Vec3 hitNormal) {
        this.bodyId = bodyId;
        this.hitFraction = hitFraction;
        this.hitNormal = hitNormal;
    }

    public int getBodyId() { return bodyId; }

    public float getHitFraction() { return hitFraction; }
    public Vec3 getHitNormal() { return hitNormal; }

    public RVec3 calculateHitPoint(RVec3 rayOrigin, Vec3 rayDirection, float maxDistance) {
        Vec3 offset = Op.star(rayDirection, this.hitFraction * maxDistance);
        return Op.plus(rayOrigin, offset);
    }
}