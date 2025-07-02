package net.xmx.xbullet.physics.object.global;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.operator.Op;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.physics.world.PhysicsWorld;

import java.util.Optional;

public class PhysicsRaytracing {

    public static final float DEFAULT_MAX_DISTANCE = 7.0f;

    public static Optional<CombinedHitResult> rayCast(Level level, RVec3 rayOrigin, Vec3 rayDirection, float maxDistance) {

        Optional<PhysicsHitInfo> physicsHit = rayCastPhysics(level, rayOrigin, rayDirection, maxDistance);

        Optional<BlockHitInfo> blockHit = rayCastBlocks(level, rayOrigin, rayDirection, maxDistance);

        if (physicsHit.isPresent() && blockHit.isPresent()) {

            if (physicsHit.get().getHitFraction() < blockHit.get().getHitFraction()) {
                return Optional.of(new CombinedHitResult(physicsHit.get()));
            } else {
                return Optional.of(new CombinedHitResult(blockHit.get()));
            }
        } else if (physicsHit.isPresent()) {

            return Optional.of(new CombinedHitResult(physicsHit.get()));
        } else if (blockHit.isPresent()) {

            return Optional.of(new CombinedHitResult(blockHit.get()));
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

    public static Optional<BlockHitInfo> rayCastBlocks(Level level, RVec3 rayOrigin, Vec3 rayDirection, float maxDistance) {
        net.minecraft.world.phys.Vec3 mcRayOrigin = new net.minecraft.world.phys.Vec3(rayOrigin.xx(), rayOrigin.yy(), rayOrigin.zz());
        net.minecraft.world.phys.Vec3 rayEnd = mcRayOrigin.add(rayDirection.getX() * maxDistance, rayDirection.getY() * maxDistance, rayDirection.getZ() * maxDistance);

        ClipContext context = new ClipContext(mcRayOrigin, rayEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null);
        BlockHitResult blockHitResult = level.clip(context);

        if (blockHitResult.getType() == HitResult.Type.BLOCK) {
            double hitDistance = blockHitResult.getLocation().distanceTo(mcRayOrigin);
            float hitFraction = (float) (hitDistance / maxDistance);

            return Optional.of(new BlockHitInfo(blockHitResult, hitFraction));
        }

        return Optional.empty();
    }

    public static class CombinedHitResult {
        private final PhysicsHitInfo physicsHit;
        private final BlockHitInfo blockHit;

        public CombinedHitResult(PhysicsHitInfo physicsHit) {
            this.physicsHit = physicsHit;
            this.blockHit = null;
        }

        public CombinedHitResult(BlockHitInfo blockHit) {
            this.physicsHit = null;
            this.blockHit = blockHit;
        }

        public boolean isPhysicsHit() {
            return physicsHit != null;
        }

        public boolean isBlockHit() {
            return blockHit != null;
        }

        public Optional<PhysicsHitInfo> getPhysicsHit() {
            return Optional.ofNullable(physicsHit);
        }

        public Optional<BlockHitInfo> getBlockHit() {
            return Optional.ofNullable(blockHit);
        }
    }

    public static class PhysicsHitInfo {
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

    public static class BlockHitInfo {
        private final BlockHitResult blockHitResult;
        private final float hitFraction;

        public BlockHitInfo(BlockHitResult blockHitResult, float hitFraction) {
            this.blockHitResult = blockHitResult;
            this.hitFraction = hitFraction;
        }

        public BlockHitResult getBlockHitResult() { return blockHitResult; }
        public float getHitFraction() { return hitFraction; }
    }
}