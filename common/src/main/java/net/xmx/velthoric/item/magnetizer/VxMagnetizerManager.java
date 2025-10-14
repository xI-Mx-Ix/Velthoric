/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.magnetizer;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.operator.Op;
import com.github.stephengold.joltjni.readonly.ConstBroadPhaseQuery;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.xmx.velthoric.physics.raycasting.VxClipContext;
import net.xmx.velthoric.physics.raycasting.VxHitResult;
import net.xmx.velthoric.physics.raycasting.VxRaycaster;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author xI-Mx-Ix
 */
public class VxMagnetizerManager {

    private static final VxMagnetizerManager INSTANCE = new VxMagnetizerManager();

    private enum MagnetMode { INACTIVE, ATTRACT, REPEL }
    private final Map<UUID, MagnetMode> playerModes = new ConcurrentHashMap<>();

    private static final float MAGNET_RADIUS = 100.0f;
    private static final float TARGET_DISTANCE = 100.0f;

    private static final float BASE_ATTRACT_FORCE = 2_000f;
    private static final float BASE_REPEL_FORCE = 3_000f;

    private VxMagnetizerManager() {}

    public static VxMagnetizerManager getInstance() {
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

        final Level level = player.level();
        var physicsWorld = VxPhysicsWorld.get(level.dimension());
        if (physicsWorld == null || !physicsWorld.isRunning()) {
            stop(player);
            return;
        }

        final net.minecraft.world.phys.Vec3 eyePos = player.getEyePosition();
        final net.minecraft.world.phys.Vec3 lookVec = player.getLookAngle();
        final net.minecraft.world.phys.Vec3 rayEnd = eyePos.add(lookVec.scale(TARGET_DISTANCE));

        VxClipContext context = new VxClipContext(eyePos, rayEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player, true);

        Optional<VxHitResult> hitResult = VxRaycaster.raycast(level, context);

        final net.minecraft.world.phys.Vec3 targetPoint;
        if (hitResult.isPresent()) {
            targetPoint = hitResult.get().getLocation();
        } else {
            targetPoint = rayEnd;
        }

        final RVec3 targetPointRVec = new RVec3((float) targetPoint.x(), (float) targetPoint.y(), (float) targetPoint.z());


        if (level instanceof ServerLevel serverLevel) {
            var particleType = mode == MagnetMode.ATTRACT ? ParticleTypes.END_ROD : ParticleTypes.FLAME;

            serverLevel.sendParticles(
                    player,
                    particleType,
                    true,
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

            ConstBroadPhaseQuery broadPhaseQuery = physicsSystem.getBroadPhaseQuery();
            var collector = new AllHitCollideShapeBodyCollector();

            var broadPhaseFilter = new BroadPhaseLayerFilter();
            var objectFilter = new ObjectLayerFilter();

            broadPhaseQuery.collideSphere(targetPointRVec.toVec3(), MAGNET_RADIUS, collector, broadPhaseFilter, objectFilter);

            int[] hitBodyIds = collector.getHits();

            for (int bodyId : hitBodyIds) {
                physicsWorld.getPhysicsSystem().getBodyInterface().activateBody(bodyId);

                try (var lock = new BodyLockWrite(physicsSystem.getBodyLockInterface(), bodyId)) {
                    if (lock.succeededAndIsInBroadPhase() && lock.getBody().isDynamic()) {
                        Body body = lock.getBody();
                        RVec3 bodyPos = body.getCenterOfMassPosition();

                        RVec3 vectorToBody = Op.minus(bodyPos, targetPointRVec);
                        double distance = vectorToBody.length();

                        if (distance < 1e-5) continue;

                        float forceMagnitude = (float) ((mode == MagnetMode.ATTRACT ? BASE_ATTRACT_FORCE : BASE_REPEL_FORCE) * distance);

                        com.github.stephengold.joltjni.Vec3 forceDirection = vectorToBody.toVec3().normalized();
                        if (mode == MagnetMode.ATTRACT) {
                            forceDirection.scaleInPlace(-1f);
                        }

                        com.github.stephengold.joltjni.Vec3 finalForce = Op.star(forceDirection, forceMagnitude);
                        body.addForce(finalForce);
                    }
                }
            }
            collector.close();
        });
    }
}
