/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.raycasting;

import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Provides high-level raycasting functionality, combining both Minecraft and physics world queries.
 * This class orchestrates raycasts and returns the closest hit from all sources.
 *
 * @author xI-Mx-Ix
 */
public final class VxRaycaster {

    private VxRaycaster() {
    }

    /**
     * Performs a comprehensive raycast that considers both Minecraft's world (blocks and entities)
     * and the Jolt physics world, returning the closest hit.
     * Terrain bodies in the physics world are automatically ignored.
     *
     * @param level   The Minecraft level.
     * @param context The context for the raycast, including start and end points.
     * @return An Optional containing the closest hit result, or an empty Optional if no hit occurred.
     */
    public static Optional<VxHitResult> raycast(Level level, VxClipContext context) {
        net.minecraft.world.phys.Vec3 from = context.getFrom();
        net.minecraft.world.phys.Vec3 to = context.getTo();

        List<VxHitResult> hits = new ArrayList<>();

        // 1. Raycast against Minecraft blocks and entities.
        raycastMinecraft(level, context).ifPresent(hits::add);

        // 2. Raycast against the physics world if requested.
        if (context.isIncludePhysics()) {
            VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(level.dimension());
            double maxDistance = from.distanceTo(to);
            net.minecraft.world.phys.Vec3 direction = to.subtract(from).normalize();
            RVec3 rayOrigin = new RVec3((float) from.x, (float) from.y, (float) from.z);
            Vec3 rayDirection = new Vec3((float) direction.x, (float) direction.y, (float) direction.z);

            // Use the dedicated physics raycaster, which automatically ignores terrain.
            raycastPhysics(physicsWorld, rayOrigin, rayDirection, (float) maxDistance).ifPresent(hits::add);
        }

        // 3. Find the closest hit among all results.
        return hits.stream().min(Comparator.comparingDouble(hit -> hit.getLocation().distanceToSqr(from)));
    }

    /**
     * Performs a raycast exclusively against Minecraft's world (blocks and entities).
     *
     * @param level   The Minecraft level.
     * @param context The context for the raycast.
     * @return An Optional containing the hit result, or an empty Optional.
     */
    public static Optional<VxHitResult> raycastMinecraft(Level level, VxClipContext context) {
        net.minecraft.world.phys.Vec3 from = context.getFrom();
        net.minecraft.world.phys.Vec3 to = context.getTo();
        HitResult blockHitResult = level.clip(context);
        double closestHitSq = blockHitResult.getType() == HitResult.Type.MISS ? Double.MAX_VALUE : blockHitResult.getLocation().distanceToSqr(from);
        HitResult finalHit = blockHitResult;
        Entity entity = context.getEntity();

        if (entity != null) {
            Predicate<Entity> entityPredicate = e -> !e.isSpectator() && e.isPickable();
            AABB searchBox = new AABB(from, to);
            EntityHitResult entityHitResult = ProjectileUtil.getEntityHitResult(level, entity, from, to, searchBox, entityPredicate);

            if (entityHitResult != null) {
                double entityHitSq = entityHitResult.getLocation().distanceToSqr(from);
                if (entityHitSq < closestHitSq) {
                    finalHit = entityHitResult;
                }
            }
        }
        return finalHit.getType() != HitResult.Type.MISS ? Optional.of(new VxHitResult(finalHit)) : Optional.empty();
    }

    /**
     * Performs a raycast exclusively against the physics world, ignoring all terrain bodies.
     *
     * @param physicsWorld The physics world instance.
     * @param rayOrigin    The starting point of the ray.
     * @param rayDirection The direction of the ray (should be normalized).
     * @param maxDistance  The maximum distance the ray should travel.
     * @return An Optional containing the hit result, or an empty Optional.
     */
    public static Optional<VxHitResult> raycastPhysics(VxPhysicsWorld physicsWorld, RVec3 rayOrigin, Vec3 rayDirection, float maxDistance) {
        // Delegate to the generic physics raycaster, providing the terrain filter.
        Optional<VxPhysicsRaycaster.Result> physicsResult = VxPhysicsRaycaster.raycast(
                physicsWorld, rayOrigin, rayDirection, maxDistance, VxObjectLayerFilters.IGNORE_TERRAIN
        );

        // Convert the generic physics result to the application-specific VxHitResult.
        return physicsResult.map(hit -> {
            RVec3 hitPointR = hit.hitPoint();
            net.minecraft.world.phys.Vec3 hitPoint = new net.minecraft.world.phys.Vec3(hitPointR.xx(), hitPointR.yy(), hitPointR.zz());
            return new VxHitResult(hitPoint, hit.bodyId(), hit.hitNormal(), hit.hitFraction());
        });
    }
}