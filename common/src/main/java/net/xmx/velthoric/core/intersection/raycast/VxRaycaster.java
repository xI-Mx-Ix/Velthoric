/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.intersection.raycast;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.operator.Op;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;
import net.xmx.velthoric.init.VxMainClass;

import java.util.Optional;

/**
 * The primary entry point for physics-based raycasting.
 * <p>
 * This class has been refactored to remove all Vanilla Minecraft logic (Block/Entity HitResults).
 * It interacts exclusively with the Jolt Physics System.
 *
 * @author xI-Mx-Ix
 */
public final class VxRaycaster {

    private VxRaycaster() {
        // Prevent instantiation
    }

    /**
     * Performs a raycast against the physics world, ignoring terrain bodies.
     * <p>
     * This method is optimized for gameplay mechanics like the Physics Gun, where
     * interaction should be limited to dynamic or interactable physics objects.
     *
     * @param physicsWorld The physics world instance.
     * @param origin       The starting point of the ray (double precision).
     * @param direction    The normalized direction of the ray.
     * @param maxDistance  The maximum length of the ray.
     * @return An Optional containing the {@link VxHitResult} if a valid body was hit.
     */
    public static Optional<VxHitResult> raycastPhysics(VxPhysicsWorld physicsWorld, RVec3 origin, Vec3 direction, float maxDistance) {
        if (physicsWorld == null || !physicsWorld.isRunning()) {
            return Optional.empty();
        }

        // Calculate the ray vector (Direction * Distance)
        Vec3 rayVector = Op.star(direction, maxDistance);

        // Use the cached filter that strictly ignores terrain
        return performRaycast(physicsWorld, origin, rayVector, VxRaycastFilters.IGNORE_TERRAIN);
    }

    /**
     * Internal implementation of the raycast using Jolt's NarrowPhaseQuery.
     *
     * @param world             The physics world.
     * @param origin            The ray origin.
     * @param directionAndDist  The ray vector (direction scaled by distance).
     * @param objectLayerFilter The filter determining which layers to hit.
     * @return The hit result.
     */
    private static Optional<VxHitResult> performRaycast(VxPhysicsWorld world, RVec3 origin, Vec3 directionAndDist, ObjectLayerFilter objectLayerFilter) {
        PhysicsSystem system = world.getPhysicsSystem();
        if (system == null) return Optional.empty();

        // Use try-with-resources for JNI objects that must be closed locally.
        // The filters (BROADPHASE_ALL, BODY_ALL, objectLayerFilter) are static/cached and NOT closed here.
        try (RRayCast ray = new RRayCast(origin, directionAndDist);
             RayCastSettings settings = new RayCastSettings();
             ClosestHitCastRayCollector collector = new ClosestHitCastRayCollector()) {

            // Execute the query
            system.getNarrowPhaseQuery().castRay(
                    ray,
                    settings,
                    collector,
                    VxRaycastFilters.BROADPHASE_ALL, // Cached BroadPhase Filter
                    objectLayerFilter,               // Cached Object Filter (e.g., IGNORE_TERRAIN)
                    VxRaycastFilters.BODY_ALL        // Cached Body Filter
            );

            if (collector.hadHit()) {
                RayCastResult hit = collector.getHit();
                
                // Retrieve data
                int bodyId = hit.getBodyId();
                float fraction = hit.getFraction();
                RVec3 hitPos = ray.getPointOnRay(fraction);

                // We need to lock the body briefly to get the exact world normal
                Vec3 normal;
                try (BodyLockRead lock = new BodyLockRead(system.getBodyLockInterface(), bodyId)) {
                    if (lock.succeededAndIsInBroadPhase()) {
                        normal = lock.getBody().getWorldSpaceSurfaceNormal(hit.getSubShapeId2(), hitPos);
                    } else {
                        // Fallback if body was removed during query (rare race condition)
                        normal = new Vec3(0, 1, 0); 
                    }
                }

                return Optional.of(new VxHitResult(bodyId, hitPos, normal, fraction));
            }

        } catch (Exception e) {
            VxMainClass.LOGGER.error("Jolt Raycast failed", e);
        }

        return Optional.empty();
    }
}