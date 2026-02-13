/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.magnetizer;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.operator.Op;
import com.github.stephengold.joltjni.readonly.ConstBodyLockInterface;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;
import net.xmx.velthoric.core.util.intersect.VxPhysicsIntersector;
import net.xmx.velthoric.core.util.raycast.VxClipContext;
import net.xmx.velthoric.core.util.raycast.VxHitResult;
import net.xmx.velthoric.core.util.raycast.VxRaycaster;
import net.xmx.velthoric.item.tool.VxToolMode;
import net.xmx.velthoric.item.tool.config.VxToolConfig;

import java.util.Optional;

/**
 * The logic implementation for the Magnetizer tool.
 * <p>
 * Primary Action (Left Click): Attracts objects towards the aim point.
 * Secondary Action (Right Click): Repels objects away from the aim point.
 *
 * @author xI-Mx-Ix
 */
public class VxMagnetizerMode extends VxToolMode {

    @Override
    public void registerProperties(VxToolConfig config) {
        config.addFloat("Range", 100.0f, 10.0f, 500.0f);
        config.addFloat("Radius", 20.0f, 1.0f, 100.0f);
        config.addFloat("Attract Force", 2000.0f, 100.0f, 10000.0f);
        config.addFloat("Repel Force", 3000.0f, 100.0f, 10000.0f);
    }

    @Override
    public void onServerTick(ServerPlayer player, VxToolConfig config, ActionState state) {
        if (state == ActionState.IDLE) {
            return;
        }

        Level level = player.level();
        VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(level.dimension());

        if (physicsWorld == null || !physicsWorld.isRunning()) {
            return;
        }

        // Retrieve config values
        float range = config.getFloat("Range");
        float radius = config.getFloat("Radius");
        float attractForce = config.getFloat("Attract Force");
        float repelForce = config.getFloat("Repel Force");

        // Perform raycast to find target point
        net.minecraft.world.phys.Vec3 eyePos = player.getEyePosition();
        net.minecraft.world.phys.Vec3 lookVec = player.getLookAngle();
        net.minecraft.world.phys.Vec3 rayEnd = eyePos.add(lookVec.scale(range));

        VxClipContext context = new VxClipContext(eyePos, rayEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player, true);
        Optional<VxHitResult> hitResult = VxRaycaster.raycast(level, context);

        net.minecraft.world.phys.Vec3 targetPoint = hitResult.map(VxHitResult::getLocation).orElse(rayEnd);
        RVec3 targetPointRVec = new RVec3((float) targetPoint.x(), (float) targetPoint.y(), (float) targetPoint.z());

        // Spawn particles for visual feedback
        if (level instanceof ServerLevel serverLevel) {
            var particleType = (state == ActionState.PRIMARY_ACTIVE) ? ParticleTypes.END_ROD : ParticleTypes.FLAME;
            serverLevel.sendParticles(
                    player, particleType, true,
                    targetPoint.x, targetPoint.y, targetPoint.z,
                    5, 0.1, 0.1, 0.1, 0.0
            );
        }

        // Apply physics forces
        physicsWorld.execute(() -> {
            // Find all bodies within the radius of the target point
            int[] intersections = VxPhysicsIntersector.broadIntersectSphere(physicsWorld, targetPointRVec.toVec3(), radius);

            if (intersections.length == 0)
                return;

            PhysicsSystem physicsSystem = physicsWorld.getPhysicsSystem();
            if (physicsSystem == null) return;

            BodyInterface bodyInterface = physicsSystem.getBodyInterface();
            ConstBodyLockInterface lockInterface = physicsSystem.getBodyLockInterface();

            for (int bodyId : intersections) {
                // Wake up the body so it can process forces
                bodyInterface.activateBody(bodyId);

                try (BodyLockWrite lock = new BodyLockWrite(lockInterface, bodyId)) {
                    if (lock.succeededAndIsInBroadPhase() && lock.getBody().isDynamic()) {
                        Body body = lock.getBody();
                        RVec3 bodyPos = body.getCenterOfMassPosition();

                        // Calculate direction and distance
                        RVec3 vectorToBody = Op.minus(bodyPos, targetPointRVec);
                        double distance = vectorToBody.length();

                        if (distance < 1e-5) continue;

                        // Normalize direction
                        Vec3 forceDirection = vectorToBody.toVec3().normalized();
                        float forceMagnitude;

                        if (state == ActionState.PRIMARY_ACTIVE) {
                            // Attract: Pull towards center (invert direction)
                            forceDirection.scaleInPlace(-1.0f);
                            forceMagnitude = attractForce * (float) distance; // Scale by distance for stability
                        } else {
                            // Repel: Push away
                            forceMagnitude = repelForce * (float) (radius - distance); // Stronger closer to center
                            if (forceMagnitude < 0) forceMagnitude = 0;
                        }

                        Vec3 finalForce = Op.star(forceDirection, forceMagnitude);
                        body.addForce(finalForce);
                    }
                }
            }
        });
    }
}