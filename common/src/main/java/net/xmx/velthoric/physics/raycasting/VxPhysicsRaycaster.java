/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.raycasting;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.operator.Op;
import com.github.stephengold.joltjni.readonly.ConstNarrowPhaseQuery;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.Optional;

/**
 * Handles raycasting operations within the Jolt physics world, completely decoupled from Minecraft.
 * This class provides a generic way to perform physics-based raycasts with customizable collision filtering.
 *
 * @author xI-Mx-Ix
 */
public final class VxPhysicsRaycaster {

    private VxPhysicsRaycaster() {
    }

    /**
     * A record to hold the raw results of a physics raycast, using only Jolt and primitive types.
     */
    public record Result(int bodyId, RVec3 hitPoint, Vec3 hitNormal, float hitFraction) {
    }

    /**
     * Performs a raycast with default filters, colliding with all possible objects.
     *
     * @param physicsWorld The physics world to perform the raycast in.
     * @param rayOrigin    The starting point of the ray.
     * @param rayDirection The direction of the ray (should be normalized).
     * @param maxDistance  The maximum distance the ray should travel.
     * @return An Optional containing a generic physics hit result if a hit occurred, otherwise an empty Optional.
     */
    public static Optional<Result> raycast(VxPhysicsWorld physicsWorld, RVec3 rayOrigin, Vec3 rayDirection, float maxDistance) {
        try (BroadPhaseLayerFilter bplFilter = new BroadPhaseLayerFilter();
             ObjectLayerFilter olFilter = new ObjectLayerFilter();
             BodyFilter bodyFilter = new BodyFilter()) {
            return raycast(physicsWorld, rayOrigin, rayDirection, maxDistance, bplFilter, olFilter, bodyFilter);
        }
    }

    /**
     * Performs a raycast using a specific object layer filter, with default broad-phase and body filters.
     *
     * @param physicsWorld      The physics world to perform the raycast in.
     * @param rayOrigin         The starting point of the ray.
     * @param rayDirection      The direction of the ray (should be normalized).
     * @param maxDistance       The maximum distance the ray should travel.
     * @param objectLayerFilter A custom filter to determine which object layers the ray can hit.
     * @return An Optional containing a generic physics hit result if a hit occurred, otherwise an empty Optional.
     */
    public static Optional<Result> raycast(VxPhysicsWorld physicsWorld, RVec3 rayOrigin, Vec3 rayDirection, float maxDistance, ObjectLayerFilter objectLayerFilter) {
        try (BroadPhaseLayerFilter bplFilter = new BroadPhaseLayerFilter();
             BodyFilter bodyFilter = new BodyFilter()) {
            return raycast(physicsWorld, rayOrigin, rayDirection, maxDistance, bplFilter, objectLayerFilter, bodyFilter);
        }
    }

    /**
     * Performs a raycast with fully customized broad-phase, object-layer, and body filters.
     * This is the most specific version of the raycast method.
     *
     * @param physicsWorld The physics world to perform the raycast in.
     * @param rayOrigin    The starting point of the ray.
     * @param rayDirection The direction of the ray (should be normalized).
     * @param maxDistance  The maximum distance the ray should travel.
     * @param bplFilter    A custom filter for broad-phase layers.
     * @param olFilter     A custom filter for object layers.
     * @param bodyFilter   A custom filter for individual physics bodies.
     * @return An Optional containing a generic physics hit result if a hit occurred, otherwise an empty Optional.
     */
    public static Optional<Result> raycast(VxPhysicsWorld physicsWorld, RVec3 rayOrigin, Vec3 rayDirection, float maxDistance,
                                           BroadPhaseLayerFilter bplFilter, ObjectLayerFilter olFilter, BodyFilter bodyFilter) {
        if (physicsWorld == null || !physicsWorld.isRunning() || physicsWorld.getPhysicsSystem() == null) {
            return Optional.empty();
        }

        PhysicsSystem physicsSystem = physicsWorld.getPhysicsSystem();
        ConstNarrowPhaseQuery narrowPhaseQuery = physicsSystem.getNarrowPhaseQuery();
        Vec3 directionAndLength = Op.star(rayDirection, maxDistance);

        try (RRayCast ray = new RRayCast(rayOrigin, directionAndLength);
             RayCastSettings settings = new RayCastSettings();
             ClosestHitCastRayCollector collector = new ClosestHitCastRayCollector()) {

            // Perform the raycast with the provided filter instances.
            narrowPhaseQuery.castRay(ray, settings, collector, bplFilter, olFilter, bodyFilter);

            if (collector.hadHit()) {
                RayCastResult hit = collector.getHit();
                float hitFraction = hit.getFraction();
                RVec3 hitPointR = ray.getPointOnRay(hitFraction);
                int bodyId = hit.getBodyId();

                try (BodyLockRead lock = new BodyLockRead(physicsSystem.getBodyLockInterface(), bodyId)) {
                    if (lock.succeededAndIsInBroadPhase()) {
                        Vec3 hitNormal = lock.getBody().getWorldSpaceSurfaceNormal(hit.getSubShapeId2(), hitPointR);
                        return Optional.of(new Result(bodyId, hitPointR, hitNormal, hitFraction));
                    }
                }
            }
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Exception during physics raycast", e);
        }
        return Optional.empty();
    }
}