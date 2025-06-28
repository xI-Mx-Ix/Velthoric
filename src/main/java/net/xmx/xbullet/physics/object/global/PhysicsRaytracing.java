package net.xmx.xbullet.physics.object.global;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.operator.Op;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.physics.core.PhysicsWorld;
import net.xmx.xbullet.physics.core.PhysicsWorldRegistry;

import java.util.Optional;

public class PhysicsRaytracing {

    public static final float DEFAULT_MAX_DISTANCE = 7.0f;

    public static boolean isBlockInTheWay(Level level, RVec3 rayOrigin, RVec3 hitPoint) {
        if (level == null) return true;

        try {
            net.minecraft.world.phys.Vec3 mcRayOrigin = new net.minecraft.world.phys.Vec3(rayOrigin.xx(), rayOrigin.yy(), rayOrigin.zz());
            net.minecraft.world.phys.Vec3 mcHitPos = new net.minecraft.world.phys.Vec3(hitPoint.xx(), hitPoint.yy(), hitPoint.zz());

            ClipContext context = new ClipContext(mcRayOrigin, mcHitPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null);
            BlockHitResult blockHit = level.clip(context);

            if (blockHit.getType() == HitResult.Type.BLOCK) {
                double blockDistSqr = blockHit.getLocation().distanceToSqr(mcRayOrigin);
                double physicsObjectDistSqr = mcHitPos.distanceToSqr(mcRayOrigin);
                return blockDistSqr < physicsObjectDistSqr - 1e-6;
            }
            return false;
        } catch (Exception e) {
            XBullet.LOGGER.warn("isBlockInTheWay: Exception during block collision check: {}", e.getMessage(), e);
            return true;
        }
    }

    public static Optional<RayHitInfo> rayCastPhysics(Level level, RVec3 rayOrigin, Vec3 rayDirection, float maxDistance) {
        PhysicsWorld physicsWorld = PhysicsWorldRegistry.getInstance().getPhysicsWorld(level.dimension());
        if (physicsWorld == null || !physicsWorld.isRunning() || physicsWorld.getPhysicsSystem() == null) {
            return Optional.empty();
        }

        PhysicsSystem physicsSystem = physicsWorld.getPhysicsSystem();
        NarrowPhaseQuery narrowPhaseQuery = physicsSystem.getNarrowPhaseQuery();
        if (narrowPhaseQuery == null) {
            return Optional.empty();
        }

        Vec3 directionAndLength = Op.star(rayDirection, maxDistance);
        RRayCast ray = new RRayCast(rayOrigin, directionAndLength);

        RayCastSettings settings = new RayCastSettings();
        ClosestHitCastRayCollector collector = new ClosestHitCastRayCollector();

        try {
            narrowPhaseQuery.castRay(ray, settings, collector);

            if (collector.hadHit()) {
                RayCastResult hit = collector.getHit();
                int bodyId = hit.getBodyId();
                int subShapeId = hit.getSubShapeId2();

                try (BodyLockRead lock = new BodyLockRead(physicsSystem.getBodyLockInterface(), bodyId)) {
                    if (lock.succeededAndIsInBroadPhase()) {
                        Body body = lock.getBody();

                        RVec3 hitPoint = ray.getPointOnRay(hit.getFraction());

                        Vec3 temporaryHitNormal = body.getWorldSpaceSurfaceNormal(subShapeId, hitPoint);

                        Vec3 persistentHitNormal = new Vec3(temporaryHitNormal);

                        RayHitInfo hitInfo = new RayHitInfo(
                                bodyId,
                                hit.getFraction(),
                                persistentHitNormal
                        );

                        return Optional.of(hitInfo);
                    }
                }
            }
        } catch (Exception e) {
            XBullet.LOGGER.error("Exception during physics raycast: {}", e.getMessage(), e);
        }

        return Optional.empty();
    }

    public static Optional<RayHitInfo> rayCastPhysicsAndFilterBlocks(
            Level level, RVec3 rayOrigin, Vec3 rayDirection, float maxDistance, String logContext) {

        Optional<RayHitInfo> physicsHitOpt = rayCastPhysics(level, rayOrigin, rayDirection, maxDistance);

        if (physicsHitOpt.isPresent()) {
            RayHitInfo physicsHit = physicsHitOpt.get();
            RVec3 hitPoint = physicsHit.calculateHitPoint(rayOrigin, rayDirection);

            if (isBlockInTheWay(level, rayOrigin, hitPoint)) {

                XBullet.LOGGER.debug("{}: Block found to be in the way before physics object hit at {}", logContext, hitPoint);
                return Optional.empty();
            } else {
                return physicsHitOpt;
            }
        }
        return Optional.empty();
    }

    public static class RayHitInfo {
        private final int bodyId;
        private final float hitFraction;
        private final Vec3 hitNormal;

        public RayHitInfo(int bodyId, float hitFraction, Vec3 hitNormal) {
            this.bodyId = bodyId;
            this.hitFraction = hitFraction;
            this.hitNormal = hitNormal;
        }

        public int getBodyId() { return bodyId; }
        public float getHitFraction() { return hitFraction; }
        public Vec3 getHitNormal() { return hitNormal; }

        public RVec3 calculateHitPoint(RVec3 rayOrigin, Vec3 rayDirection) {

            Vec3 offset = Op.star(rayDirection, this.hitFraction * DEFAULT_MAX_DISTANCE);
            return Op.plus(rayOrigin, offset);
        }
    }
}