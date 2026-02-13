/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.chaincreator;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.operator.Op;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.xmx.velthoric.builtin.VxRegisteredBodies;
import net.xmx.velthoric.core.body.server.VxServerBodyManager;
import net.xmx.velthoric.core.body.type.VxBody;
import net.xmx.velthoric.core.constraint.manager.VxConstraintManager;
import net.xmx.velthoric.core.intersection.raycast.VxHitResult;
import net.xmx.velthoric.core.intersection.raycast.VxRaycaster;
import net.xmx.velthoric.core.intersection.raycast.util.VxRaycastUtil;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;
import net.xmx.velthoric.item.chaincreator.body.VxChainPartRigidBody;
import net.xmx.velthoric.item.tool.VxToolMode;
import net.xmx.velthoric.item.tool.config.VxToolConfig;
import net.xmx.velthoric.math.VxTransform;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The tool mode implementation for the Chain Creator.
 * <p>
 * This mode allows players to create complex physics chains between two arbitrary points in the world.
 * It leverages the Jolt Physics engine for high-fidelity constraints and uses a hybrid raycasting
 * approach to anchor chains to either dynamic physics bodies or the static Minecraft world.
 *
 * @author xI-Mx-Ix
 */
public class VxChainCreatorMode extends VxToolMode {

    /**
     * Thread-safe mapping of player UUIDs to their selected starting attachment point.
     */
    private final Map<UUID, AttachmentInfo> startPoints = new ConcurrentHashMap<>();

    /**
     * Registers configurable properties for the chain creator tool.
     *
     * @param config The tool configuration instance to populate.
     */
    @Override
    public void registerProperties(VxToolConfig config) {
        // Defines the thickness of each chain link.
        config.addFloat("Chain Radius", 0.1f, 0.05f, 0.5f);
        // Defines the target length for individual chain segments.
        config.addFloat("Segment Length", 0.25f, 0.1f, 1.0f);
    }

    /**
     * Handles the transition between different action states for the tool.
     * <p>
     * Logic flow:
     * - Pressing primary: Sets the start point.
     * - Releasing primary: Sets the end point and triggers chain generation.
     *
     * @param player The player performing the action.
     * @param state  The new state being applied.
     */
    @Override
    public void setState(ServerPlayer player, ActionState state) {
        // Fetch the state prior to this update.
        ActionState previousState = getState(player);
        // Apply the base state update logic.
        super.setState(player, state);

        // Detect transition from IDLE to PRIMARY_ACTIVE (Initial Press).
        if (previousState == ActionState.IDLE && state == ActionState.PRIMARY_ACTIVE) {
            handleStart(player);
        }
        // Detect transition from PRIMARY_ACTIVE back to IDLE (Button Release).
        else if (previousState == ActionState.PRIMARY_ACTIVE && state == ActionState.IDLE) {
            handleFinish(player);
        }
    }

    /**
     * Periodic server-side tick logic for the tool mode.
     *
     * @param player The player holding the tool.
     * @param config The current configuration for the tool.
     * @param state  The current action state of the player.
     */
    @Override
    public void onServerTick(ServerPlayer player, VxToolConfig config, ActionState state) {
        // Chain Creator currently uses discrete events (Start/Finish) and requires no tick logic.
    }

    /**
     * Identifies and stores the starting anchor point for a new chain.
     *
     * @param player The player initiating the chain creation.
     */
    private void handleStart(ServerPlayer player) {
        // Use the prioritized raycast to find a valid attachment point.
        findAttachment(player).ifPresent(info -> {
            // Map the start point to the player's unique identifier.
            startPoints.put(player.getUUID(), info);
            // Retrieve position for sound feedback.
            RVec3 pos = info.worldPosition;
            // Provide localized audio feedback to the player.
            player.level().playSound(null,
                    new BlockPos((int) pos.xx(), (int) pos.yy(), (int) pos.zz()),
                    SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0f, 1.0f);
        });
    }

    /**
     * Completes the chain by finding an end anchor and spawning physics bodies.
     *
     * @param player The player finishing the chain creation.
     */
    private void handleFinish(ServerPlayer player) {
        // Atomically remove the start point to avoid double-execution.
        AttachmentInfo startInfo = startPoints.remove(player.getUUID());
        // If no start point was selected, abort.
        if (startInfo == null) return;

        // Perform raycast for the second endpoint.
        findAttachment(player).ifPresent(endInfo -> {
            // Retrieve position for sound feedback.
            RVec3 pos = endInfo.worldPosition;
            // Play sound indicating the chain is being forged.
            player.level().playSound(null,
                    new BlockPos((int) pos.xx(), (int) pos.yy(), (int) pos.zz()),
                    SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1.0f, 1.0f);

            // Fetch the dimensions physics world.
            VxPhysicsWorld world = VxPhysicsWorld.get(player.level().dimension());
            if (world != null) {
                // Get current tool configuration values.
                VxToolConfig config = getConfig(player.getUUID());
                float radius = config.getFloat("Chain Radius");
                float segmentLen = config.getFloat("Segment Length");

                // Execute the actual construction on the dedicated physics thread.
                world.execute(() -> createChain(world, startInfo, endInfo, radius, segmentLen));
            }
        });
    }

    /**
     * Logic for constructing a chain composed of rigid bodies and constraints in Jolt.
     *
     * @param world                The physics world to create bodies in.
     * @param startInfo            Anchor data for the beginning of the chain.
     * @param endInfo              Anchor data for the end of the chain.
     * @param radius               The radius (thickness) of the chain parts.
     * @param desiredSegmentLength The requested length for each segment.
     */
    private void createChain(VxPhysicsWorld world, AttachmentInfo startInfo, AttachmentInfo endInfo, float radius, float desiredSegmentLength) {
        // Access core management systems.
        VxServerBodyManager bodyManager = world.getBodyManager();
        VxConstraintManager constraintManager = world.getConstraintManager();
        BodyInterface bodyInterface = world.getPhysicsSystem().getBodyInterface();

        // Avoid creating a chain that anchors to the same point on a single body (excluding world).
        if (startInfo.bodyUUID.equals(endInfo.bodyUUID) && !startInfo.bodyUUID.equals(VxConstraintManager.WORLD_BODY_ID)) {
            return;
        }

        // Wake up anchor bodies to ensure simulation reacts immediately.
        VxBody startBody = bodyManager.getVxBody(startInfo.bodyUUID);
        if (startBody != null) bodyInterface.activateBody(startBody.getBodyId());
        VxBody endBody = bodyManager.getVxBody(endInfo.bodyUUID);
        if (endBody != null) bodyInterface.activateBody(endBody.getBodyId());

        // Calculate world distance and direction between anchors.
        RVec3 startPos = startInfo.worldPosition;
        RVec3 endPos = endInfo.worldPosition;
        RVec3 vector = Op.minus(endPos, startPos);
        double distance = vector.length();

        // If distance is too small for even one segment, abort.
        if (distance < desiredSegmentLength) return;

        // Calculate segment count and distribute length evenly.
        int numSegments = Math.max(1, (int) Math.ceil(distance / desiredSegmentLength));
        double actualSegmentLength = distance / numSegments;

        // Establish the orientation and increment vectors.
        RVec3 directionR = vector.normalized();
        Vec3 direction = directionR.toVec3();
        Quat orientation = Quat.sFromTo(new Vec3(0, 1, 0), direction);
        Vec3 localAxisY = new Vec3(0, 1, 0);
        Vec3 localAxisX = new Vec3(1, 0, 0);
        RVec3 segmentVector = Op.star(actualSegmentLength, directionR);

        // Keep track of the link to connect to.
        UUID previousBodyUuid = startInfo.bodyUUID;
        RVec3 pivotOnPreviousBody = startInfo.localPivot;

        // Iteratively create segments and connect them.
        for (int i = 0; i < numSegments; ++i) {
            // Calculate spatial properties for the new segment.
            RVec3 segmentStartPos = Op.plus(startPos, Op.star(i, segmentVector));
            RVec3 segmentCenterPos = Op.plus(segmentStartPos, Op.star(0.5, segmentVector));

            // Instantiate the physical representation of the chain link.
            VxChainPartRigidBody currentBody = bodyManager.createRigidBody(
                    VxRegisteredBodies.CHAIN_PART,
                    new VxTransform(segmentCenterPos, orientation),
                    body -> {
                        body.setServerData(VxChainPartRigidBody.getLengthAccessor(), (float) actualSegmentLength);
                        body.setServerData(VxChainPartRigidBody.getRadiusAccessor(), radius);
                    }
            );
            if (currentBody == null) continue;

            // Activate newly created link.
            bodyInterface.activateBody(currentBody.getBodyId());

            // Connection point at the bottom of the current link.
            RVec3 pivotOnCurrentBodyLocal = new RVec3(0, -actualSegmentLength / 2.0, 0);

            // Configure the SwingTwist constraint (simulates a ball-and-socket with twist limits).
            try (SwingTwistConstraintSettings settings = new SwingTwistConstraintSettings()) {
                // High step count for chain stability.
                settings.setNumPositionStepsOverride(30);
                settings.setNumVelocityStepsOverride(30);
                // Allow full conical swing (360 degrees total).
                settings.setNormalHalfConeAngle((float) Math.PI);
                settings.setPlaneHalfConeAngle((float) Math.PI);
                settings.setSwingType(com.github.stephengold.joltjni.enumerate.ESwingType.Cone);
                // Lock twist for chain-like behavior.
                settings.setTwistMinAngle(0.0f);
                settings.setTwistMaxAngle(0.0f);

                // Handle the first connection (potentially to the static world).
                if (previousBodyUuid.equals(VxConstraintManager.WORLD_BODY_ID)) {
                    settings.setSpace(EConstraintSpace.WorldSpace);
                    settings.setPosition1(pivotOnPreviousBody);
                    settings.setTwistAxis1(direction);

                    // Compute axis rotation for plane alignment.
                    RMat44 rotationMat = new RMat44();
                    rotationMat.sRotation(orientation);
                    Vec3 worldAxisX = rotationMat.multiply3x3(localAxisX);
                    settings.setPlaneAxis1(worldAxisX);

                    // Offset the world position of pivot 2 to match the segment center.
                    Vec3 rotatedPivot = rotationMat.multiply3x3(pivotOnCurrentBodyLocal.toVec3());
                    RVec3 pivotOnCurrentBodyWorld = Op.plus(segmentCenterPos, new RVec3(rotatedPivot));

                    settings.setPosition2(pivotOnCurrentBodyWorld);
                    settings.setTwistAxis2(direction);
                    settings.setPlaneAxis2(worldAxisX);
                } else {
                    // Standard segment-to-segment connection in COM local space.
                    settings.setSpace(EConstraintSpace.LocalToBodyCom);
                    settings.setPosition1(pivotOnPreviousBody);
                    settings.setTwistAxis1(localAxisY);
                    settings.setPlaneAxis1(localAxisX);

                    settings.setPosition2(pivotOnCurrentBodyLocal);
                    settings.setTwistAxis2(localAxisY);
                    settings.setPlaneAxis2(localAxisX);
                }
                // Register the constraint in the manager.
                constraintManager.createConstraint(settings, previousBodyUuid, currentBody.getPhysicsId());
            }

            // Update indices for next link.
            previousBodyUuid = currentBody.getPhysicsId();
            pivotOnPreviousBody = new RVec3(0, actualSegmentLength / 2.0, 0);
        }

        // Link the final segment to the end anchor point.
        try (SwingTwistConstraintSettings settings = new SwingTwistConstraintSettings()) {
            settings.setNumPositionStepsOverride(30);
            settings.setNumVelocityStepsOverride(30);
            settings.setNormalHalfConeAngle((float) Math.PI);
            settings.setPlaneHalfConeAngle((float) Math.PI);
            settings.setSwingType(com.github.stephengold.joltjni.enumerate.ESwingType.Cone);
            settings.setTwistMinAngle(0.0f);
            settings.setTwistMaxAngle(0.0f);

            VxBody lastLinkBody = bodyManager.getVxBody(previousBodyUuid);
            if (lastLinkBody == null) return;

            if (endInfo.bodyUUID.equals(VxConstraintManager.WORLD_BODY_ID)) {
                // Connect final link to the static world anchor.
                settings.setSpace(EConstraintSpace.WorldSpace);
                RMat44 lastLinkTransform = bodyInterface.getCenterOfMassTransform(lastLinkBody.getBodyId());
                RVec3 pivotOnLastLinkWorld = lastLinkTransform.multiply3x4(pivotOnPreviousBody);

                Vec3 axisYWorld = lastLinkTransform.multiply3x3(localAxisY);
                Vec3 axisXWorld = lastLinkTransform.multiply3x3(localAxisX);

                settings.setPosition1(pivotOnLastLinkWorld);
                settings.setTwistAxis1(axisYWorld);
                settings.setPlaneAxis1(axisXWorld);

                settings.setPosition2(endInfo.localPivot);
                settings.setTwistAxis2(axisYWorld);
                settings.setPlaneAxis2(axisXWorld);
            } else {
                // Standard COM local connection to a target physics body.
                settings.setSpace(EConstraintSpace.LocalToBodyCom);
                settings.setPosition1(pivotOnPreviousBody);
                settings.setTwistAxis1(localAxisY);
                settings.setPlaneAxis1(localAxisX);

                settings.setPosition2(endInfo.localPivot);
                settings.setTwistAxis2(localAxisY);
                settings.setPlaneAxis2(localAxisX);
            }
            constraintManager.createConstraint(settings, previousBodyUuid, endInfo.bodyUUID);
        }
    }

    /**
     * Resolves spatial attachment info, prioritizing high-fidelity physics objects over static world terrain.
     *
     * @param player The player aiming.
     * @return Optional attachment data if a valid target was hit.
     */
    private Optional<AttachmentInfo> findAttachment(ServerPlayer player) {
        // Access dimension specific physics world.
        VxPhysicsWorld world = VxPhysicsWorld.get(player.level().dimension());
        if (world == null) return Optional.empty();

        // Standard tool reach calculation.
        double reachDistance = player.isCreative() ? 5.0 : 4.5;
        net.minecraft.world.phys.Vec3 from = player.getEyePosition();
        net.minecraft.world.phys.Vec3 look = player.getLookAngle();
        net.minecraft.world.phys.Vec3 to = from.add(look.scale(reachDistance));

        // Prepare Jolt-ready vectors.
        RVec3 joltFrom = new RVec3((float) from.x, (float) from.y, (float) from.z);
        Vec3 joltLook = new Vec3((float) look.x, (float) look.y, (float) look.z);

        // 1. Check for dynamic/kinematic physics bodies (Optimized Raycast).
        Optional<VxHitResult> physicsHitOpt = VxRaycaster.raycastPhysics(world, joltFrom, joltLook, (float) reachDistance);

        // 2. Check for Minecraft terrain (Block/Liquid Raycast).
        Optional<BlockHitResult> blockHitOpt = VxRaycastUtil.raycastBlocks(player.level(), from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player);

        // Calculate squared distances to determine the closest target.
        double physDistSq = physicsHitOpt.map(h -> Op.minus(h.getPhysicsHit().get().position(), joltFrom).lengthSq()).orElse(Double.MAX_VALUE);
        double blockDistSq = blockHitOpt.map(h -> h.getLocation().distanceToSqr(from)).orElse(Double.MAX_VALUE);

        // If a physics body is hit and is closer than terrain, attach to it.
        if (physDistSq < blockDistSq && physicsHitOpt.isPresent()) {
            VxHitResult.PhysicsHit hit = physicsHitOpt.get().getPhysicsHit().get();
            VxBody hitBody = world.getBodyManager().getByJoltBodyId(hit.bodyId());

            if (hitBody != null && hitBody.getBodyId() != 0) {
                // Calculate local COM offset for the pivot point.
                BodyInterface bodyInterface = world.getPhysicsSystem().getBodyInterface();
                RMat44 inverseTransform = bodyInterface.getCenterOfMassTransform(hitBody.getBodyId()).inversed();
                RVec3 localPivot = inverseTransform.multiply3x4(hit.position());
                return Optional.of(new AttachmentInfo(hitBody.getPhysicsId(), hit.position(), localPivot));
            }
        }

        // If terrain is hit (or is closer), attach to the static world.
        if (blockHitOpt.isPresent()) {
            net.minecraft.world.phys.Vec3 bPos = blockHitOpt.get().getLocation();
            RVec3 worldPosition = new RVec3(bPos.x, bPos.y, bPos.z);
            // World body anchor uses identical world and local coordinates.
            return Optional.of(new AttachmentInfo(VxConstraintManager.WORLD_BODY_ID, worldPosition, worldPosition));
        }

        // No valid target found.
        return Optional.empty();
    }

    /**
     * Internal container for anchoring information.
     *
     * @param bodyUUID      Unique identifier of the physics body (or world).
     * @param worldPosition Global world coordinates of the attachment point.
     * @param localPivot    Coordinates relative to the body's center of mass.
     */
    private record AttachmentInfo(UUID bodyUUID, RVec3 worldPosition, RVec3 localPivot) {
    }
}