/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.raycasting;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.operator.Op;
import com.github.stephengold.joltjni.readonly.ConstNarrowPhaseQuery;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.physics.object.type.VxBody;
import net.xmx.velthoric.physics.riding.RidingManager;
import net.xmx.velthoric.physics.riding.seat.Seat;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.function.Predicate;

public final class VxRaytracing {

    public static final float DEFAULT_MAX_DISTANCE = 7.0f;

    private VxRaytracing() {
    }

    public static Optional<VxHitResult> raycast(Level level, VxClipContext context) {
        Vec3 from = context.getFrom();
        Vec3 to = context.getTo();

        List<VxHitResult> hits = new ArrayList<>();

        raycastMinecraftInternal(level, context).ifPresent(hits::add);

        if (context.isIncludePhysics()) {
            double maxDistance = from.distanceTo(to);
            Vec3 direction = to.subtract(from).normalize();
            RVec3 rayOrigin = new RVec3((float) from.x, (float) from.y, (float) from.z);
            com.github.stephengold.joltjni.Vec3 rayDirection = new com.github.stephengold.joltjni.Vec3((float) direction.x, (float) direction.y, (float) direction.z);
            raycastPhysicsInternal(level, rayOrigin, rayDirection, (float) maxDistance).ifPresent(hits::add);
        }

        if (context.isIncludeSeats()) {
            raycastSeatsInternal(level, from, to).ifPresent(hits::add);
        }

        return hits.stream().min(Comparator.comparingDouble(hit -> hit.getLocation().distanceToSqr(from)));
    }

    public static Optional<VxHitResult> raycastMinecraft(Level level, VxClipContext context) {
        return raycastMinecraftInternal(level, context);
    }

    public static Optional<VxHitResult> raycastPhysics(Level level, RVec3 rayOrigin, com.github.stephengold.joltjni.Vec3 rayDirection, float maxDistance) {
        return raycastPhysicsInternal(level, rayOrigin, rayDirection, maxDistance);
    }

    public static Optional<VxHitResult> raycastSeats(Level level, Vec3 rayOrigin, Vec3 rayEnd) {
        return raycastSeatsInternal(level, rayOrigin, rayEnd);
    }

    private static Optional<VxHitResult> raycastMinecraftInternal(Level level, VxClipContext context) {
        Vec3 from = context.getFrom();
        Vec3 to = context.getTo();
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

    private static Optional<VxHitResult> raycastPhysicsInternal(Level level, RVec3 rayOrigin, com.github.stephengold.joltjni.Vec3 rayDirection, float maxDistance) {
        VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(level.dimension());
        if (physicsWorld == null || !physicsWorld.isRunning() || physicsWorld.getPhysicsSystem() == null) return Optional.empty();

        PhysicsSystem physicsSystem = physicsWorld.getPhysicsSystem();
        ConstNarrowPhaseQuery narrowPhaseQuery = physicsSystem.getNarrowPhaseQuery();
        com.github.stephengold.joltjni.Vec3 directionAndLength = Op.star(rayDirection, maxDistance);
        try (RRayCast ray = new RRayCast(rayOrigin, directionAndLength); RayCastSettings settings = new RayCastSettings(); ClosestHitCastRayCollector collector = new ClosestHitCastRayCollector()) {
            narrowPhaseQuery.castRay(ray, settings, collector);
            if (collector.hadHit()) {
                RayCastResult hit = collector.getHit();
                float hitFraction = hit.getFraction();
                RVec3 hitPointR = ray.getPointOnRay(hitFraction);
                Vec3 hitPoint = new Vec3(hitPointR.xx(), hitPointR.yy(), hitPointR.zz());
                int bodyId = hit.getBodyId();
                try (BodyLockRead lock = new BodyLockRead(physicsSystem.getBodyLockInterface(), bodyId)) {
                    if (lock.succeededAndIsInBroadPhase()) {
                        com.github.stephengold.joltjni.Vec3 hitNormal = lock.getBody().getWorldSpaceSurfaceNormal(hit.getSubShapeId2(), hitPointR);
                        return Optional.of(new VxHitResult(hitPoint, bodyId, new com.github.stephengold.joltjni.Vec3(hitNormal), hitFraction));
                    }
                }
            }
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Exception during physics raycast", e);
        }
        return Optional.empty();
    }

    private static Optional<VxHitResult> raycastSeatsInternal(Level level, Vec3 rayOrigin, Vec3 rayEnd) {
        VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(level.dimension());
        if (physicsWorld == null || !physicsWorld.isRunning() || physicsWorld.getObjectManager() == null) return Optional.empty();

        RidingManager ridingManager = physicsWorld.getRidingManager();
        VxHitResult closestHit = null;
        double minFraction = Double.MAX_VALUE;

        for (Map.Entry<UUID, Map<String, Seat>> entry : ridingManager.getObjectToSeatsMap().entrySet()) {
            UUID objectId = entry.getKey();
            Map<String, Seat> seats = entry.getValue();
            if (seats.isEmpty()) continue;

            VxBody obj = physicsWorld.getObjectManager().getObject(objectId);
            if (obj == null) continue;

            VxTransform transform = obj.getTransform();
            Quaternionf worldRot = transform.getRotation(new Quaternionf());
            Vector3f worldPos = transform.getTranslation(new Vector3f());
            Quaternionf invRot = worldRot.conjugate(new Quaternionf());

            Vec3 localRayStart = transformToLocal(rayOrigin, worldPos, invRot);
            Vec3 localRayEnd = transformToLocal(rayEnd, worldPos, invRot);

            for (Seat seat : seats.values()) {
                if (seat.isLocked() || ridingManager.isSeatOccupied(objectId, seat)) continue;

                Optional<Vec3> hitOpt = seat.getLocalAABB().clip(localRayStart, localRayEnd);
                if (hitOpt.isPresent()) {
                    Vec3 localHitPoint = hitOpt.get();
                    double distSq = localHitPoint.distanceToSqr(localRayStart);
                    double rayLengthSq = localRayStart.distanceToSqr(localRayEnd);
                    if (rayLengthSq > 1.0E-7) {
                        double fraction = Math.sqrt(distSq / rayLengthSq);
                        if (fraction < minFraction) {
                            minFraction = fraction;
                            com.github.stephengold.joltjni.Vec3 worldNormal = calculateHitNormal(localHitPoint, seat.getLocalAABB(), worldRot);
                            Vec3 worldHitPoint = rayOrigin.add(rayEnd.subtract(rayOrigin).scale(fraction));
                            closestHit = new VxHitResult(worldHitPoint, obj.getBodyId(), seat.getName(), worldNormal, (float) fraction);
                        }
                    }
                }
            }
        }
        return Optional.ofNullable(closestHit);
    }

    private static Vec3 transformToLocal(Vec3 worldVec, Vector3f pos, Quaternionf invRot) {
        Vector3f tempVec = new Vector3f((float) worldVec.x, (float) worldVec.y, (float) worldVec.z);
        tempVec.sub(pos);
        invRot.transform(tempVec);
        return new Vec3(tempVec.x, tempVec.y, tempVec.z);
    }

    private static com.github.stephengold.joltjni.Vec3 calculateHitNormal(Vec3 localHit, AABB localAABB, Quaternionf worldRot) {
        final float epsilon = 1.0E-5f;
        Vector3f localNormal = new Vector3f();
        if (Math.abs(localHit.x - localAABB.minX) < epsilon) localNormal.x = -1;
        else if (Math.abs(localHit.x - localAABB.maxX) < epsilon) localNormal.x = 1;
        else if (Math.abs(localHit.y - localAABB.minY) < epsilon) localNormal.y = -1;
        else if (Math.abs(localHit.y - localAABB.maxY) < epsilon) localNormal.y = 1;
        else if (Math.abs(localHit.z - localAABB.minZ) < epsilon) localNormal.z = -1;
        else if (Math.abs(localHit.z - localAABB.maxZ) < epsilon) localNormal.z = 1;
        worldRot.transform(localNormal);
        return new com.github.stephengold.joltjni.Vec3(localNormal.x, localNormal.y, localNormal.z);
    }
}
