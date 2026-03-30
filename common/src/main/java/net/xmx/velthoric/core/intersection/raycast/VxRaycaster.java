/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.intersection.raycast;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.operator.Op;
import com.github.stephengold.joltjni.readonly.RVec3Arg;
import com.github.stephengold.joltjni.readonly.Vec3Arg;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;
import net.xmx.velthoric.init.VxMainClass;

import java.util.*;

/**
 * The primary entry point for physics-based raycasting.
 *
 * @author xI-Mx-Ix
 * @author timtaran
 */
public final class VxRaycaster {

    private VxRaycaster() {
        // Prevent instantiation
    }

    /**
     * Performs a raycast against the physics world, ignoring terrain bodies, and returns closest hit.
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
    public static Optional<VxHitResult> raycastClosest(VxPhysicsWorld physicsWorld, RVec3Arg origin, Vec3Arg direction, float maxDistance) {
        if (physicsWorld == null || !physicsWorld.isRunning()) {
            return Optional.empty();
        }

        // Calculate the ray vector (Direction * Distance)
        Vec3Arg rayVector = Op.star(direction, maxDistance);

        // Use the cached filter that strictly ignores terrain
        return raycastClosestInternal(physicsWorld, origin, rayVector, VxRaycastFilters.BROADPHASE_ALL, VxRaycastFilters.IGNORE_TERRAIN, VxRaycastFilters.BODY_ALL);
    }

    /**
     * Performs a raycast using fully customized object-layer filters, and returns closest hit.
     *
     * @param physicsWorld          The physics world.
     * @param origin                The ray origin.
     * @param direction             The normalized direction of the ray.
     * @param maxDistance           The maximum length of the ray.
     * @param objectLayerFilter     The filter determining which object layers to hit.
     * @return The hit result.
     */
    public static Optional<VxHitResult> raycastClosest(VxPhysicsWorld physicsWorld, RVec3Arg origin, Vec3Arg direction,
                                                float maxDistance, ObjectLayerFilter objectLayerFilter) {
        if (physicsWorld == null || !physicsWorld.isRunning()) {
            return Optional.empty();
        }

        // Calculate the ray vector (Direction * Distance)
        Vec3Arg rayVector = Op.star(direction, maxDistance);

        return raycastClosestInternal(physicsWorld, origin, rayVector, VxRaycastFilters.BROADPHASE_ALL, objectLayerFilter, VxRaycastFilters.BODY_ALL);
    }

    /**
     * Performs a raycast using fully customized broad-phase, object-layer, and body filters, and returns closest hit.
     *
     * @param physicsWorld          The physics world.
     * @param origin                The ray origin.
     * @param direction             The normalized direction of the ray.
     * @param maxDistance           The maximum length of the ray.
     * @param broadPhaseLayerFilter The filter determining which broad-phase layers to hit.
     * @param objectLayerFilter     The filter determining which object layers to hit.
     * @param bodyFilter            The filter determining which bodies to hit.
     * @return The hit result.
     */
    public static Optional<VxHitResult> raycastClosest(VxPhysicsWorld physicsWorld, RVec3Arg origin, Vec3Arg direction,
                                                float maxDistance, BroadPhaseLayerFilter broadPhaseLayerFilter,
                                                ObjectLayerFilter objectLayerFilter, BodyFilter bodyFilter) {
        if (physicsWorld == null || !physicsWorld.isRunning()) {
            return Optional.empty();
        }

        // Calculate the ray vector (Direction * Distance)
        Vec3Arg rayVector = Op.star(direction, maxDistance);

        return raycastClosestInternal(physicsWorld, origin, rayVector, broadPhaseLayerFilter, objectLayerFilter, bodyFilter);
    }

    /**
     * Internal implementation of the raycast using Jolt's {@link NarrowPhaseQuery}.
     *
     * @param physicsWorld          The physics world.
     * @param origin                The ray origin.
     * @param directionAndDist      The ray vector (direction scaled by distance).
     * @param broadPhaseLayerFilter The filter determining which broad-phase layers to hit.
     * @param objectLayerFilter     The filter determining which object layers to hit.
     * @param bodyFilter            The filter determining which bodies to hit.
     * @return The hit result.
     */
    private static Optional<VxHitResult> raycastClosestInternal(VxPhysicsWorld physicsWorld, RVec3Arg origin, Vec3Arg directionAndDist,
                                                         BroadPhaseLayerFilter broadPhaseLayerFilter, ObjectLayerFilter objectLayerFilter,
                                                         BodyFilter bodyFilter) {
        PhysicsSystem system = physicsWorld.getPhysicsSystem();
        if (system == null) return Optional.empty();

        // Use try-with-resources for JNI objects that must be closed locally.
        try (RRayCast ray = new RRayCast(origin, directionAndDist);
             RayCastSettings settings = new RayCastSettings();
             ClosestHitCastRayCollector collector = new ClosestHitCastRayCollector()) {

            // Execute the query
            system.getNarrowPhaseQuery().castRay(
                    ray,
                    settings,
                    collector,
                    broadPhaseLayerFilter,
                    objectLayerFilter,
                    bodyFilter
            );

            if (collector.hadHit()) {
                RayCastResult hit = collector.getHit();

                // Retrieve data
                int bodyId = hit.getBodyId();
                float fraction = hit.getFraction();
                RVec3Arg hitPos = ray.getPointOnRay(fraction);

                // We need to lock the body briefly to get the exact world normal
                Vec3Arg normal;
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

    /**
     * Performs a raycast against the physics world, ignoring terrain bodies, and returns closest hit.
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
    public static List<VxHitResult> raycastAll(VxPhysicsWorld physicsWorld, RVec3Arg origin, Vec3Arg direction, float maxDistance) {
        if (physicsWorld == null || !physicsWorld.isRunning()) {
            return Collections.emptyList();
        }

        // Calculate the ray vector (Direction * Distance)
        Vec3Arg rayVector = Op.star(direction, maxDistance);

        // Use the cached filter that strictly ignores terrain
        return raycastAllInternal(physicsWorld, origin, rayVector, VxRaycastFilters.BROADPHASE_ALL, VxRaycastFilters.IGNORE_TERRAIN, VxRaycastFilters.BODY_ALL);
    }

    /**
     * Performs a raycast using fully customized object-layer filters, and returns closest hit.
     *
     * @param physicsWorld          The physics world.
     * @param origin                The ray origin.
     * @param direction             The normalized direction of the ray.
     * @param maxDistance           The maximum length of the ray.
     * @param objectLayerFilter     The filter determining which object layers to hit.
     * @return The hit result.
     */
    public static List<VxHitResult> raycastAll(VxPhysicsWorld physicsWorld, RVec3Arg origin, Vec3Arg direction,
                                                       float maxDistance, ObjectLayerFilter objectLayerFilter) {
        if (physicsWorld == null || !physicsWorld.isRunning()) {
            return Collections.emptyList();
        }

        // Calculate the ray vector (Direction * Distance)
        Vec3Arg rayVector = Op.star(direction, maxDistance);

        return raycastAllInternal(physicsWorld, origin, rayVector, VxRaycastFilters.BROADPHASE_ALL, objectLayerFilter, VxRaycastFilters.BODY_ALL);
    }

    /**
     * Performs a raycast using fully customized broad-phase, object-layer, and body filters, and returns closest hit.
     *
     * @param physicsWorld          The physics world.
     * @param origin                The ray origin.
     * @param direction             The normalized direction of the ray.
     * @param maxDistance           The maximum length of the ray.
     * @param broadPhaseLayerFilter The filter determining which broad-phase layers to hit.
     * @param objectLayerFilter     The filter determining which object layers to hit.
     * @param bodyFilter            The filter determining which bodies to hit.
     * @return The hit result.
     */
    public static List<VxHitResult> raycastAll(VxPhysicsWorld physicsWorld, RVec3Arg origin, Vec3Arg direction,
                                                       float maxDistance, BroadPhaseLayerFilter broadPhaseLayerFilter,
                                                       ObjectLayerFilter objectLayerFilter, BodyFilter bodyFilter) {
        if (physicsWorld == null || !physicsWorld.isRunning()) {
            return Collections.emptyList();
        }

        // Calculate the ray vector (Direction * Distance)
        Vec3Arg rayVector = Op.star(direction, maxDistance);

        return raycastAllInternal(physicsWorld, origin, rayVector, broadPhaseLayerFilter, objectLayerFilter, bodyFilter);
    }

    /**
     * Internal implementation of the raycast using Jolt's {@link NarrowPhaseQuery}.
     *
     * @param physicsWorld          The physics world.
     * @param origin                The ray origin.
     * @param directionAndDist      The ray vector (direction scaled by distance).
     * @param broadPhaseLayerFilter The filter determining which broad-phase layers to hit.
     * @param objectLayerFilter     The filter determining which object layers to hit.
     * @param bodyFilter            The filter determining which bodies to hit.
     * @return The hit result.
     */
    private static List<VxHitResult> raycastAllInternal(VxPhysicsWorld physicsWorld, RVec3Arg origin, Vec3Arg directionAndDist,
                                                        BroadPhaseLayerFilter broadPhaseLayerFilter, ObjectLayerFilter objectLayerFilter,
                                                        BodyFilter bodyFilter) {
        PhysicsSystem system = physicsWorld.getPhysicsSystem();
        if (system == null) return Collections.emptyList();

        // Use try-with-resources for JNI objects that must be closed locally.
        try (RRayCast ray = new RRayCast(origin, directionAndDist);
             RayCastSettings settings = new RayCastSettings();
             AllHitCastRayCollector collector = new AllHitCastRayCollector()) {

            // Execute the query
            system.getNarrowPhaseQuery().castRay(
                    ray,
                    settings,
                    collector,
                    broadPhaseLayerFilter,
                    objectLayerFilter,
                    bodyFilter
            );

            List<RayCastResult> hits = collector.getHits();
            int size = hits.size();

            if (size != 0) {
                List<VxHitResult> results = new ArrayList<>(size);

                for (RayCastResult hit : hits) {
                    // Retrieve data
                    int bodyId = hit.getBodyId();
                    float fraction = hit.getFraction();
                    RVec3Arg hitPos = ray.getPointOnRay(fraction);

                    // We need to lock the body briefly to get the exact world normal
                    Vec3Arg normal;
                    try (BodyLockRead lock = new BodyLockRead(system.getBodyLockInterface(), bodyId)) {
                        if (lock.succeededAndIsInBroadPhase()) {
                            normal = lock.getBody().getWorldSpaceSurfaceNormal(hit.getSubShapeId2(), hitPos);
                        } else {
                            // Fallback if body was removed during query (rare race condition)
                            normal = new Vec3(0, 1, 0);
                        }
                    }

                    results.add(new VxHitResult(bodyId, hitPos, normal, fraction));
                }

                return results;
            }

        } catch (Exception e) {
            VxMainClass.LOGGER.error("Jolt Raycast failed", e);
        }

        return Collections.emptyList();
    }
}