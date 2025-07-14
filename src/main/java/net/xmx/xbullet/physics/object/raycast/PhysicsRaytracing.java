package net.xmx.xbullet.physics.object.raycast;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.operator.Op;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.physics.object.raycast.info.MinecraftHitInfo;
import net.xmx.xbullet.physics.object.raycast.info.PhysicsHitInfo;
import net.xmx.xbullet.physics.object.raycast.result.CombinedHitResult;
import net.xmx.xbullet.physics.world.PhysicsWorld;

import java.util.Optional;
import java.util.function.Predicate;

public final class PhysicsRaytracing {

    public static final float DEFAULT_MAX_DISTANCE = 7.0f;

    private PhysicsRaytracing() {}

    public static Optional<CombinedHitResult> rayCast(Level level, RVec3 rayOrigin, Vec3 rayDirection, float maxDistance) {
        Optional<PhysicsHitInfo> physicsHit = rayCastPhysics(level, rayOrigin, rayDirection, maxDistance);
        Optional<MinecraftHitInfo> minecraftHit = rayCastMinecraft(level, rayOrigin, rayDirection, maxDistance);

        if (physicsHit.isPresent() && minecraftHit.isPresent()) {
            if (physicsHit.get().getHitFraction() < minecraftHit.get().getHitFraction()) {
                return Optional.of(new CombinedHitResult(physicsHit.get()));
            } else {

                return Optional.of(new CombinedHitResult(minecraftHit.get()));
            }
        } else if (physicsHit.isPresent()) {
            return Optional.of(new CombinedHitResult(physicsHit.get()));
        } else if (minecraftHit.isPresent()) {

            return Optional.of(new CombinedHitResult(minecraftHit.get()));
        }

        return Optional.empty();
    }

    public static Optional<MinecraftHitInfo> rayCastMinecraft(Level level, RVec3 rayOrigin, Vec3 rayDirection, float maxDistance) {
        net.minecraft.world.phys.Vec3 mcRayOrigin = new net.minecraft.world.phys.Vec3(rayOrigin.xx(), rayOrigin.yy(), rayOrigin.zz());
        net.minecraft.world.phys.Vec3 rayEnd = mcRayOrigin.add(rayDirection.getX() * maxDistance, rayDirection.getY() * maxDistance, rayDirection.getZ() * maxDistance);

        ClipContext blockClipContext = new ClipContext(mcRayOrigin, rayEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null);
        HitResult blockHitResult = level.clip(blockClipContext);

        double currentClosestHitSq = blockHitResult.getType() == HitResult.Type.MISS
                ? maxDistance * maxDistance
                : blockHitResult.getLocation().distanceToSqr(mcRayOrigin);

        Predicate<Entity> entityPredicate = entity -> !entity.isSpectator() && entity.isPickable();

        AABB searchBox = new AABB(mcRayOrigin, rayEnd);

        EntityHitResult entityHitResult = ProjectileUtil.getEntityHitResult(
                level,
                null,
                mcRayOrigin,
                rayEnd,
                searchBox,
                entityPredicate
        );

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

        PhysicsWorld physicsWorld = PhysicsWorld.get(level.dimension());
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
            XBullet.LOGGER.error("Exception during physics raycast", e);
        }

        return Optional.empty();
    }
}