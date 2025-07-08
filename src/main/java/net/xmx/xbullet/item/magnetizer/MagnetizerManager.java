package net.xmx.xbullet.item.magnetizer;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.operator.Op;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.xbullet.physics.object.global.PhysicsRaytracing;
import net.xmx.xbullet.physics.object.physicsobject.pcmd.ActivateBodyCommand;
import net.xmx.xbullet.physics.world.PhysicsWorld;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MagnetizerManager {

    private static final MagnetizerManager INSTANCE = new MagnetizerManager();

    private enum MagnetMode { INACTIVE, ATTRACT, REPEL }
    private final Map<UUID, MagnetMode> playerModes = new ConcurrentHashMap<>();

    // GEÄNDERT: Reichweite wie von dir gewünscht auf 100 erhöht.
    private static final float MAGNET_RADIUS = 100.0f;
    private static final float TARGET_DISTANCE = 100.0f;

    // GEÄNDERT: Die Basiskräfte wurden drastisch reduziert.
    // BEGRÜNDUNG: Da wir jetzt die Kraft mit der Distanz multiplizieren statt zu teilen,
    // würden die alten, hohen Werte zu extrem instabilen Kräften führen.
    // Diese neuen Werte sind ein guter Ausgangspunkt für das neue Kraftmodell.
    private static final float BASE_ATTRACT_FORCE = 2_000f;
    private static final float BASE_REPEL_FORCE = 3_000f;

    private MagnetizerManager() {}

    public static MagnetizerManager getInstance() {
        return INSTANCE;
    }

    public void startAttract(ServerPlayer player) {
        playerModes.put(player.getUUID(), MagnetMode.ATTRACT);
    }

    public void startRepel(ServerPlayer player) {
        playerModes.put(player.getUUID(), MagnetMode.REPEL);
    }

    public void stop(ServerPlayer player) {
        playerModes.put(player.getUUID(), MagnetMode.INACTIVE);
    }

    public boolean isActing(ServerPlayer player) {
        return playerModes.getOrDefault(player.getUUID(), MagnetMode.INACTIVE) != MagnetMode.INACTIVE;
    }

    public void serverTick(ServerPlayer player) {
        final MagnetMode mode = playerModes.getOrDefault(player.getUUID(), MagnetMode.INACTIVE);
        if (mode == MagnetMode.INACTIVE) return;

        var physicsWorld = PhysicsWorld.get(player.level().dimension());
        if (physicsWorld == null || !physicsWorld.isRunning()) {
            stop(player);
            return;
        }

        final var eyePos = player.getEyePosition();
        final var lookVec = player.getLookAngle();
        final RVec3 rayOrigin = new RVec3(eyePos.x, eyePos.y, eyePos.z);
        final Vec3 rayDirection = new Vec3(lookVec.x, lookVec.y, lookVec.z).normalized();
        Optional<PhysicsRaytracing.CombinedHitResult> hitResult = PhysicsRaytracing.rayCast(player.level(), rayOrigin, rayDirection, TARGET_DISTANCE);

        final RVec3 targetPointRVec;
        if (hitResult.isPresent()) {
            PhysicsRaytracing.CombinedHitResult hit = hitResult.get();
            if (hit.isPhysicsHit()) {
                targetPointRVec = hit.getPhysicsHit().get().calculateHitPoint(rayOrigin, rayDirection, TARGET_DISTANCE);
            } else {
                var mcHitPos = hit.getBlockHit().get().getBlockHitResult().getLocation();
                targetPointRVec = new RVec3(mcHitPos.x, mcHitPos.y, mcHitPos.z);
            }
        } else {
            Vec3 offset = Op.star(rayDirection, TARGET_DISTANCE);
            targetPointRVec = Op.plus(rayOrigin, offset);
        }

        if (player.level() instanceof ServerLevel serverLevel) {
            var particleType = mode == MagnetMode.ATTRACT ? ParticleTypes.END_ROD : ParticleTypes.FLAME;
            serverLevel.sendParticles(
                    particleType,
                    targetPointRVec.x(),
                    targetPointRVec.y(),
                    targetPointRVec.z(),
                    5,
                    0.1,
                    0.1,
                    0.1,
                    0.0
            );
        }

        physicsWorld.execute(() -> {
            PhysicsSystem physicsSystem = physicsWorld.getPhysicsSystem();
            if (physicsSystem == null) return;

            BroadPhaseQuery broadPhaseQuery = physicsSystem.getBroadPhaseQuery();
            var collector = new AllHitCollideShapeBodyCollector();

            var broadPhaseFilter = new BroadPhaseLayerFilter();
            var objectFilter = new ObjectLayerFilter();

            broadPhaseQuery.collideSphere(targetPointRVec.toVec3(), MAGNET_RADIUS, collector, broadPhaseFilter, objectFilter);

            int[] hitBodyIds = collector.getHits();

            for (int bodyId : hitBodyIds) {
                physicsWorld.queueCommand(new ActivateBodyCommand(bodyId));

                try (var lock = new BodyLockWrite(physicsSystem.getBodyLockInterface(), bodyId)) {
                    if (lock.succeededAndIsInBroadPhase() && lock.getBody().isDynamic()) {
                        Body body = lock.getBody();
                        RVec3 bodyPos = body.getCenterOfMassPosition();

                        RVec3 vectorToBody = Op.minus(bodyPos, targetPointRVec);
                        double distanceSq = vectorToBody.lengthSq();

                        double distance = Math.sqrt(distanceSq);

                        float forceMagnitude = (float) ((mode == MagnetMode.ATTRACT ? BASE_ATTRACT_FORCE : BASE_REPEL_FORCE) * distance);

                        Vec3 forceDirection = vectorToBody.toVec3().normalized();
                        if (mode == MagnetMode.ATTRACT) {
                            forceDirection.scaleInPlace(-1f);
                        }

                        Vec3 finalForce = Op.star(forceDirection, forceMagnitude);
                        body.addForce(finalForce);
                    }
                }
            }
            collector.close();
        });
    }
}