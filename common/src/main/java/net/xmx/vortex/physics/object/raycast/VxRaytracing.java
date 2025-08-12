package net.xmx.vortex.physics.object.raycast;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.operator.Op;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.xmx.vortex.init.VxMainClass;
import net.xmx.vortex.math.VxTransform;
import net.xmx.vortex.physics.object.physicsobject.VxAbstractBody;
import net.xmx.vortex.physics.object.raycast.info.MinecraftHitInfo;
import net.xmx.vortex.physics.object.raycast.info.PhysicsHitInfo;
import net.xmx.vortex.physics.object.raycast.result.CombinedHitResult;
import net.xmx.vortex.physics.object.riding.Rideable;
import net.xmx.vortex.physics.object.riding.seat.Seat;
import net.xmx.vortex.physics.world.VxPhysicsWorld;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Optional;
import java.util.function.Predicate;

public final class VxRaytracing {

    public static final float DEFAULT_MAX_DISTANCE = 7.0f;

    private VxRaytracing() {}

    public static Optional<CombinedHitResult> rayCast(Level level, RVec3 rayOrigin, Vec3 rayDirection, float maxDistance, Entity entity) {
        Optional<PhysicsHitInfo> physicsHit = rayCastPhysics(level, rayOrigin, rayDirection, maxDistance);
        Optional<MinecraftHitInfo> minecraftHit = rayCastMinecraft(level, rayOrigin, rayDirection, maxDistance, entity);
        Optional<PhysicsHitInfo> seatHit = rayCastSeats(level, rayOrigin, rayDirection, maxDistance);

        PhysicsHitInfo bestPhysicsHit = null;
        if (physicsHit.isPresent()) {
            bestPhysicsHit = physicsHit.get();
        }
        if (seatHit.isPresent()) {
            if (bestPhysicsHit == null || seatHit.get().getHitFraction() < bestPhysicsHit.getHitFraction()) {
                bestPhysicsHit = seatHit.get();
            }
        }
        Optional<PhysicsHitInfo> finalPhysicsHit = Optional.ofNullable(bestPhysicsHit);

        if (finalPhysicsHit.isPresent() && minecraftHit.isPresent()) {
            if (finalPhysicsHit.get().getHitFraction() < minecraftHit.get().getHitFraction()) {
                return Optional.of(new CombinedHitResult(finalPhysicsHit.get()));
            } else {
                return Optional.of(new CombinedHitResult(minecraftHit.get()));
            }
        } else if (finalPhysicsHit.isPresent()) {
            return Optional.of(new CombinedHitResult(finalPhysicsHit.get()));
        } else if (minecraftHit.isPresent()) {
            return Optional.of(new CombinedHitResult(minecraftHit.get()));
        }

        return Optional.empty();
    }

    private static Optional<PhysicsHitInfo> rayCastSeats(Level level, RVec3 rayOrigin, Vec3 rayDirection, float maxDistance) {
        VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(level.dimension());
        if (physicsWorld == null || !physicsWorld.isRunning() || physicsWorld.getObjectManager() == null) {
            return Optional.empty();
        }

        PhysicsHitInfo closestHit = null;
        float minFraction = Float.MAX_VALUE;

        net.minecraft.world.phys.Vec3 mcRayOrigin = new net.minecraft.world.phys.Vec3(rayOrigin.xx(), rayOrigin.yy(), rayOrigin.zz());
        net.minecraft.world.phys.Vec3 mcRayEnd = mcRayOrigin.add(rayDirection.getX() * maxDistance, rayDirection.getY() * maxDistance, rayDirection.getZ() * maxDistance);

        for (VxAbstractBody obj : physicsWorld.getObjectManager().getObjectContainer().getAllObjects()) {
            if (obj instanceof Rideable rideable && rideable.defineSeats().length > 0) {
                VxTransform transform = obj.getGameTransform();
                Quaternionf worldRot = transform.getRotation(new Quaternionf());
                Vector3f worldPos = transform.getTranslation(new Vector3f());

                Quaternionf invRot = worldRot.conjugate(new Quaternionf());

                net.minecraft.world.phys.Vec3 localRayStart = transformToLocal(mcRayOrigin, worldPos, invRot);
                net.minecraft.world.phys.Vec3 localRayEnd = transformToLocal(mcRayEnd, worldPos, invRot);

                for (Seat seat : rideable.defineSeats()) {
                    Optional<net.minecraft.world.phys.Vec3> hitOpt = seat.getLocalAABB().clip(localRayStart, localRayEnd);

                    if (hitOpt.isPresent()) {
                        double distSq = hitOpt.get().distanceToSqr(localRayStart);
                        double rayLength = localRayStart.distanceTo(localRayEnd);
                        float fraction = (float) (Math.sqrt(distSq) / rayLength);

                        if (fraction < minFraction) {
                            minFraction = fraction;
                            Vec3 worldNormal = calculateHitNormal(hitOpt.get(), seat.getLocalAABB(), worldRot);
                            closestHit = new PhysicsHitInfo(obj.getBodyId(), fraction, worldNormal);
                        }
                    }
                }
            }
        }
        return Optional.ofNullable(closestHit);
    }

    private static net.minecraft.world.phys.Vec3 transformToLocal(net.minecraft.world.phys.Vec3 worldVec, Vector3f pos, Quaternionf invRot) {
        Vector3f tempVec = new Vector3f((float) worldVec.x, (float) worldVec.y, (float) worldVec.z);
        tempVec.sub(pos);
        invRot.transform(tempVec);
        return new net.minecraft.world.phys.Vec3(tempVec.x, tempVec.y, tempVec.z);
    }

    private static Vec3 calculateHitNormal(net.minecraft.world.phys.Vec3 localHit, AABB localAABB, Quaternionf worldRot) {
        final float epsilon = 1.0E-5f;
        Vector3f localNormal = new Vector3f();

        if (Math.abs(localHit.x - localAABB.minX) < epsilon) localNormal.x = -1;
        else if (Math.abs(localHit.x - localAABB.maxX) < epsilon) localNormal.x = 1;
        else if (Math.abs(localHit.y - localAABB.minY) < epsilon) localNormal.y = -1;
        else if (Math.abs(localHit.y - localAABB.maxY) < epsilon) localNormal.y = 1;
        else if (Math.abs(localHit.z - localAABB.minZ) < epsilon) localNormal.z = -1;
        else if (Math.abs(localHit.z - localAABB.maxZ) < epsilon) localNormal.z = 1;

        worldRot.transform(localNormal);
        return new Vec3(localNormal.x, localNormal.y, localNormal.z);
    }

    public static Optional<MinecraftHitInfo> rayCastMinecraft(Level level, RVec3 rayOrigin, Vec3 rayDirection, float maxDistance, Entity entity) {
        net.minecraft.world.phys.Vec3 mcRayOrigin = new net.minecraft.world.phys.Vec3(rayOrigin.xx(), rayOrigin.yy(), rayOrigin.zz());
        net.minecraft.world.phys.Vec3 rayEnd = mcRayOrigin.add(rayDirection.getX() * maxDistance, rayDirection.getY() * maxDistance, rayDirection.getZ() * maxDistance);

        ClipContext blockClipContext = new ClipContext(mcRayOrigin, rayEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity);
        HitResult blockHitResult = level.clip(blockClipContext);

        double currentClosestHitSq = blockHitResult.getType() == HitResult.Type.MISS
                ? maxDistance * maxDistance
                : blockHitResult.getLocation().distanceToSqr(mcRayOrigin);

        Predicate<Entity> entityPredicate = e -> !e.isSpectator() && e.isPickable();
        AABB searchBox = new AABB(mcRayOrigin, rayEnd);
        EntityHitResult entityHitResult = ProjectileUtil.getEntityHitResult(level, entity, mcRayOrigin, rayEnd, searchBox, entityPredicate);

        if (entityHitResult != null) {
            double entityHitSq = entityHitResult.getLocation().distanceToSqr(mcRayOrigin);
            if (entityHitSq < currentClosestHitSq) {
                float hitFraction = (float) (Math.sqrt(entityHitSq) / maxDistance);
                return Optional.of(new MinecraftHitInfo(entityHitResult, hitFraction));
            }
        }

        if (blockHitResult.getType() == HitResult.Type.BLOCK) {
            float hitFraction = (float) (Math.sqrt(currentClosestHitSq) / maxDistance);
            return Optional.of(new MinecraftHitInfo(blockHitResult, hitFraction));
        }

        return Optional.empty();
    }

    public static Optional<PhysicsHitInfo> rayCastPhysics(Level level, RVec3 rayOrigin, Vec3 rayDirection, float maxDistance) {
        VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(level.dimension());
        if (physicsWorld == null || !physicsWorld.isRunning() || physicsWorld.getPhysicsSystem() == null) {
            return Optional.empty();
        }

        PhysicsSystem physicsSystem = physicsWorld.getPhysicsSystem();
        NarrowPhaseQuery narrowPhaseQuery = physicsSystem.getNarrowPhaseQuery();

        Vec3 directionAndLength = Op.star(rayDirection, maxDistance);
        try (RRayCast ray = new RRayCast(rayOrigin, directionAndLength);
             RayCastSettings settings = new RayCastSettings();
             ClosestHitCastRayCollector collector = new ClosestHitCastRayCollector()) {

            narrowPhaseQuery.castRay(ray, settings, collector);

            if (collector.hadHit()) {
                RayCastResult hit = collector.getHit();
                int bodyId = hit.getBodyId();

                try (BodyLockRead lock = new BodyLockRead(physicsSystem.getBodyLockInterface(), bodyId)) {
                    if (lock.succeededAndIsInBroadPhase()) {
                        RVec3 hitPoint = ray.getPointOnRay(hit.getFraction());
                        Vec3 hitNormal = lock.getBody().getWorldSpaceSurfaceNormal(hit.getSubShapeId2(), hitPoint);

                        return Optional.of(new PhysicsHitInfo(
                                bodyId,
                                hit.getFraction(),
                                new Vec3(hitNormal)
                        ));
                    }
                }
            }
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Exception during physics raycast", e);
        }

        return Optional.empty();
    }
}