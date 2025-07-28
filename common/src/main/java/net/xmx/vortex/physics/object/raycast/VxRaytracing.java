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
import net.xmx.vortex.physics.object.raycast.info.MinecraftHitInfo;
import net.xmx.vortex.physics.object.raycast.info.PhysicsHitInfo;
import net.xmx.vortex.physics.object.raycast.result.CombinedHitResult;
import net.xmx.vortex.physics.world.VxPhysicsWorld;

import java.util.Optional;
import java.util.function.Predicate;

public final class VxRaytracing {

    public static final float DEFAULT_MAX_DISTANCE = 7.0f;

    private VxRaytracing() {}

    /**
     * Führt einen Raycast sowohl in der Physik- als auch in der Minecraft-Welt durch.
     *
     * @param level Die Welt, in der der Raycast stattfindet.
     * @param rayOrigin Der Startpunkt des Strahls.
     * @param rayDirection Die Richtung des Strahls (sollte normalisiert sein).
     * @param maxDistance Die maximale Entfernung des Strahls.
     * @param entity Die Entität, die den Raycast durchführt, um als Kontext für den Clip zu dienen.
     * @return Ein optionales CombinedHitResult, das den nächstgelegenen Treffer enthält.
     */
    // MODIFIZIERT: Die Methode akzeptiert jetzt eine 'Entity' als Parameter.
    public static Optional<CombinedHitResult> rayCast(Level level, RVec3 rayOrigin, Vec3 rayDirection, float maxDistance, Entity entity) {
        Optional<PhysicsHitInfo> physicsHit = rayCastPhysics(level, rayOrigin, rayDirection, maxDistance);
        // MODIFIZIERT: Die 'entity' wird an die rayCastMinecraft-Methode weitergegeben.
        Optional<MinecraftHitInfo> minecraftHit = rayCastMinecraft(level, rayOrigin, rayDirection, maxDistance, entity);

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

    /**
     * Führt einen Raycast nur gegen Minecraft-Blöcke und -Entitäten durch.
     *
     * @param entity Die Entität, die den Raycast durchführt. Wird für den ClipContext benötigt.
     */
    // MODIFIZIERT: Die Methode akzeptiert jetzt eine 'Entity' als Parameter.
    public static Optional<MinecraftHitInfo> rayCastMinecraft(Level level, RVec3 rayOrigin, Vec3 rayDirection, float maxDistance, Entity entity) {
        net.minecraft.world.phys.Vec3 mcRayOrigin = new net.minecraft.world.phys.Vec3(rayOrigin.xx(), rayOrigin.yy(), rayOrigin.zz());
        net.minecraft.world.phys.Vec3 rayEnd = mcRayOrigin.add(rayDirection.getX() * maxDistance, rayDirection.getY() * maxDistance, rayDirection.getZ() * maxDistance);

        // KORREKTUR: Die übergebene 'entity' wird hier anstelle von 'null' verwendet, um die NullPointerException zu beheben.
        ClipContext blockClipContext = new ClipContext(mcRayOrigin, rayEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity);
        HitResult blockHitResult = level.clip(blockClipContext);

        double currentClosestHitSq = blockHitResult.getType() == HitResult.Type.MISS
                ? maxDistance * maxDistance
                : blockHitResult.getLocation().distanceToSqr(mcRayOrigin);

        Predicate<Entity> entityPredicate = e -> !e.isSpectator() && e.isPickable();

        AABB searchBox = new AABB(mcRayOrigin, rayEnd);

        // KORREKTUR: Die 'entity' wird auch hier übergeben, um den korrekten Kontext für den Entity-Raycast zu gewährleisten.
        EntityHitResult entityHitResult = ProjectileUtil.getEntityHitResult(
                level,
                entity, // Statt 'null' wird die Entität übergeben
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

    /**
     * Führt einen Raycast nur in der Jolt-Physikwelt durch.
     */
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