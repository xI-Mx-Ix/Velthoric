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
import net.minecraft.world.phys.BlockHitResult;
import net.xmx.velthoric.core.intersection.VxPhysicsIntersector;
import net.xmx.velthoric.core.intersection.raycast.VxHitResult;
import net.xmx.velthoric.core.intersection.raycast.VxRaycaster;
import net.xmx.velthoric.core.intersection.raycast.util.VxRaycastUtil;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;
import net.xmx.velthoric.item.tool.VxToolMode;
import net.xmx.velthoric.item.tool.config.VxToolConfig;

import java.util.Optional;

/**
 * The logic implementation for the Magnetizer tool.
 * <p>
 * This tool allows players to manipulate physics objects within a specific radius
 * around a target point, either attracting them or repelling them with physical force.
 * It uses a hybrid raycasting approach to find the magnet's center point.
 *
 * @author xI-Mx-Ix
 */
public class VxMagnetizerMode extends VxToolMode {

    /**
     * Defines the configurable properties for the Magnetizer tool.
     *
     * @param config The tool configuration object to register properties to.
     */
    @Override
    public void registerProperties(VxToolConfig config) {
        // The maximum distance the player can aim to place the magnet's center.
        config.addFloat("Range", 100.0f, 10.0f, 500.0f);
        // The spherical area of effect around the magnet's center.
        config.addFloat("Radius", 20.0f, 1.0f, 100.0f);
        // Force applied when pulling objects inward.
        config.addFloat("Attract Force", 2000.0f, 100.0f, 10000.0f);
        // Force applied when pushing objects outward.
        config.addFloat("Repel Force", 3000.0f, 100.0f, 10000.0f);
    }

    /**
     * Logic executed every server tick while the tool is active.
     *
     * @param player The player holding the tool.
     * @param config The current tool settings.
     * @param state  The current action state (Primary, Secondary, or Idle).
     */
    @Override
    public void onServerTick(ServerPlayer player, VxToolConfig config, ActionState state) {
        // Do nothing if the player is not actively clicking.
        if (state == ActionState.IDLE) {
            return;
        }

        // Access the dimension-specific level and physics world.
        Level level = player.level();
        VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(level.dimension());

        // Abort if the physics simulation is not active.
        if (physicsWorld == null || !physicsWorld.isRunning()) {
            return;
        }

        // Cache configuration values for the current tick.
        float range = config.getFloat("Range");
        float radius = config.getFloat("Radius");
        float attractForce = config.getFloat("Attract Force");
        float repelForce = config.getFloat("Repel Force");

        // Determine ray start (eyes) and default end point (max range in look direction).
        net.minecraft.world.phys.Vec3 eyePos = player.getEyePosition();
        net.minecraft.world.phys.Vec3 lookVec = player.getLookAngle();
        net.minecraft.world.phys.Vec3 rayEnd = eyePos.add(lookVec.scale(range));

        // Prepare Jolt-compatible vectors for physics raycasting.
        RVec3 joltOrigin = new RVec3((float) eyePos.x, (float) eyePos.y, (float) eyePos.z);
        Vec3 joltDir = new Vec3((float) lookVec.x, (float) lookVec.y, (float) lookVec.z);

        // 1. Raycast against dynamic Jolt physics bodies.
        Optional<VxHitResult> physicsHitOpt = VxRaycaster.raycastPhysics(physicsWorld, joltOrigin, joltDir, range);

        // 2. Raycast against static Minecraft blocks/terrain.
        Optional<BlockHitResult> blockHitOpt = VxRaycastUtil.raycastBlocks(level, eyePos, rayEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player);

        // Calculate squared distances to find the closest valid hit.
        double physDistSq = physicsHitOpt.map(h -> Op.minus(h.getPhysicsHit().get().position(), joltOrigin).lengthSq()).orElse(Double.MAX_VALUE);
        double blockDistSq = blockHitOpt.map(h -> h.getLocation().distanceToSqr(eyePos)).orElse(Double.MAX_VALUE);

        // Initialize the target center point for the magnetic field.
        RVec3 targetPointRVec;
        net.minecraft.world.phys.Vec3 particlePos;

        // Select the closest collision point, or use the ray's end if nothing was hit.
        if (physDistSq < blockDistSq && physicsHitOpt.isPresent()) {
            targetPointRVec = physicsHitOpt.get().getPhysicsHit().get().position();
            particlePos = new net.minecraft.world.phys.Vec3(targetPointRVec.xx(), targetPointRVec.yy(), targetPointRVec.zz());
        } else if (blockHitOpt.isPresent()) {
            net.minecraft.world.phys.Vec3 bHit = blockHitOpt.get().getLocation();
            targetPointRVec = new RVec3(bHit.x, bHit.y, bHit.z);
            particlePos = bHit;
        } else {
            targetPointRVec = new RVec3(rayEnd.x, rayEnd.y, rayEnd.z);
            particlePos = rayEnd;
        }

        // Visual feedback: Spawn particles at the magnet's center point.
        if (level instanceof ServerLevel serverLevel) {
            var particleType = (state == ActionState.PRIMARY_ACTIVE) ? ParticleTypes.END_ROD : ParticleTypes.FLAME;
            serverLevel.sendParticles(
                    player, particleType, true,
                    particlePos.x, particlePos.y, particlePos.z,
                    5, 0.1, 0.1, 0.1, 0.0
            );
        }

        // Force Application: Offload to the dedicated physics thread for thread-safety.
        physicsWorld.execute(() -> {
            // Retrieve IDs of all physics bodies overlapping with the magnet's spherical volume.
            int[] intersections = VxPhysicsIntersector.broadIntersectSphere(physicsWorld, targetPointRVec.toVec3(), radius);

            // Abort if no bodies are within range.
            if (intersections.length == 0) return;

            // Access the low-level Jolt system components.
            PhysicsSystem physicsSystem = physicsWorld.getPhysicsSystem();
            if (physicsSystem == null) return;

            BodyInterface bodyInterface = physicsSystem.getBodyInterface();
            ConstBodyLockInterface lockInterface = physicsSystem.getBodyLockInterface();

            // Iterate through every body found in the radius.
            for (int bodyId : intersections) {
                // Wake the body from sleep so it can respond to force immediately.
                bodyInterface.activateBody(bodyId);

                // Lock the body for modification.
                try (BodyLockWrite lock = new BodyLockWrite(lockInterface, bodyId)) {
                    // Only apply forces to valid, dynamic (simulated) objects.
                    if (lock.succeededAndIsInBroadPhase() && lock.getBody().isDynamic()) {
                        Body body = lock.getBody();
                        RVec3 bodyPos = body.getCenterOfMassPosition();

                        // Calculate the vector from the magnet center to the body's center.
                        RVec3 vectorToBody = Op.minus(bodyPos, targetPointRVec);
                        double distance = vectorToBody.length();

                        // Avoid division by zero or extremely small distances.
                        if (distance < 1e-5) continue;

                        // Calculate the normalized direction vector.
                        Vec3 forceDirection = vectorToBody.toVec3().normalized();
                        float forceMagnitude;

                        // Primary Action: Attract (Pull objects toward the center).
                        if (state == ActionState.PRIMARY_ACTIVE) {
                            // Reverse the direction to point toward the magnet center.
                            forceDirection = Op.minus(forceDirection);
                            // Magnitude increases with distance for a "spring-like" attraction.
                            forceMagnitude = attractForce * (float) distance;
                        }
                        // Secondary Action: Repel (Push objects away from the center).
                        else {
                            // Magnitude is strongest at the center and decays toward the radius limit.
                            forceMagnitude = repelForce * (float) (radius - distance);
                            if (forceMagnitude < 0) forceMagnitude = 0;
                        }

                        // Compute the final force vector.
                        Vec3 finalForce = Op.star(forceMagnitude, forceDirection);
                        // Apply force at the object's center of mass.
                        body.addForce(finalForce);
                    }
                }
            }
        });
    }
}