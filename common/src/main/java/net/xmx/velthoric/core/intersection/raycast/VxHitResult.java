/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.intersection.raycast;

import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;

import java.util.Optional;

/**
 * A lightweight, physics-only hit result container.
 * <p>
 * This class no longer depends on Minecraft's {@code HitResult} or {@code ClipContext}.
 * It purely holds data regarding Jolt physics collisions.
 *
 * @author xI-Mx-Ix
 */
public class VxHitResult {

    private final PhysicsHit physicsHit;

    /**
     * Constructs a new physics hit result.
     *
     * @param bodyId      The Jolt Body ID of the hit object.
     * @param position    The precise world-space position of the hit (double precision).
     * @param normal      The surface normal at the hit position.
     * @param hitFraction The fraction of the ray distance where the hit occurred (0.0 - 1.0).
     */
    public VxHitResult(int bodyId, RVec3 position, Vec3 normal, float hitFraction) {
        this.physicsHit = new PhysicsHit(bodyId, position, normal, hitFraction);
    }

    /**
     * Retrieves the physics hit data.
     *
     * @return An Optional containing the physics hit record.
     */
    public Optional<PhysicsHit> getPhysicsHit() {
        return Optional.of(physicsHit);
    }

    /**
     * A record representing the raw data of a physics collision.
     *
     * @param bodyId      The unique identifier of the Jolt body.
     * @param position    The hit position in Jolt coordinates.
     * @param hitNormal   The surface normal at the point of impact.
     * @param hitFraction The distance fraction (0 to 1) along the ray.
     */
    public record PhysicsHit(int bodyId, RVec3 position, Vec3 hitNormal, float hitFraction) {
    }
}