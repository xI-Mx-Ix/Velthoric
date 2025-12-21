/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.item.chaincreator;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.operator.Op;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.timtaran.interactivemc.physics.builtin.VxRegisteredBodies;
import net.timtaran.interactivemc.physics.item.chaincreator.body.VxChainPartRigidBody;
import net.timtaran.interactivemc.physics.item.tool.VxToolMode;
import net.timtaran.interactivemc.physics.item.tool.config.VxToolConfig;
import net.timtaran.interactivemc.physics.math.VxTransform;
import net.timtaran.interactivemc.physics.physics.body.manager.VxBodyManager;
import net.timtaran.interactivemc.physics.physics.body.type.VxBody;
import net.timtaran.interactivemc.physics.physics.constraint.manager.VxConstraintManager;
import net.timtaran.interactivemc.physics.physics.raycasting.VxClipContext;
import net.timtaran.interactivemc.physics.physics.raycasting.VxHitResult;
import net.timtaran.interactivemc.physics.physics.raycasting.VxRaycaster;
import net.timtaran.interactivemc.physics.physics.world.VxPhysicsWorld;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The tool mode implementation for the Chain Creator.
 * <p>
 * This mode allows players to create physics-based chains between two points.
 * Pressing the primary button selects the start point, and releasing it selects
 * the end point and constructs the chain segments and constraints.
 *
 * @author xI-Mx-Ix
 */
public class VxChainCreatorMode extends VxToolMode {

    /**
     * Stores the starting hit result for each player currently using the tool.
     */
    private final Map<UUID, VxHitResult> startPoints = new ConcurrentHashMap<>();

    @Override
    public void registerProperties(VxToolConfig config) {
        config.addFloat("Chain Radius", 0.1f, 0.05f, 0.5f);
        config.addFloat("Segment Length", 0.25f, 0.1f, 1.0f);
    }

    /**
     * Intercepts state changes to handle the "Press" and "Release" logic.
     * <p>
     * When the state transitions to {@link ActionState#PRIMARY_ACTIVE}, the start point is recorded.
     * When the state transitions to {@link ActionState#IDLE} (release), the end point is recorded
     * and the chain is created.
     *
     * @param player The player changing state.
     * @param state  The new state.
     */
    @Override
    public void setState(ServerPlayer player, ActionState state) {
        ActionState previousState = getState(player);
        super.setState(player, state);

        // Handle "Press" -> Start Creation
        if (previousState == ActionState.IDLE && state == ActionState.PRIMARY_ACTIVE) {
            handleStart(player);
        }
        // Handle "Release" -> Finish Creation
        else if (previousState == ActionState.PRIMARY_ACTIVE && state == ActionState.IDLE) {
            handleFinish(player);
        }
    }

    @Override
    public void onServerTick(ServerPlayer player, VxToolConfig config, ActionState state) {
        // No continuous tick logic required for the chain creator.
        // Visualization logic (e.g., particles between start point and player view) could go here.
    }

    /**
     * Performs the initial raycast to determine the anchor point for the start of the chain.
     */
    private void handleStart(ServerPlayer player) {
        performRaycast(player).ifPresent(hitResult -> {
            startPoints.put(player.getUUID(), hitResult);
            // Play a sound to confirm the start point selection
            player.level().playSound(null,
                    new BlockPos((int) hitResult.getLocation().x, (int) hitResult.getLocation().y, (int) hitResult.getLocation().z),
                    SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0f, 1.0f);
        });
    }

    /**
     * Performs the final raycast and constructs the chain if a valid start point exists.
     */
    private void handleFinish(ServerPlayer player) {
        VxHitResult startHit = startPoints.remove(player.getUUID());
        if (startHit == null) return;

        performRaycast(player).ifPresent(endHit -> {
            // Play a sound to confirm creation
            player.level().playSound(null,
                    new BlockPos((int) endHit.getLocation().x, (int) endHit.getLocation().y, (int) endHit.getLocation().z),
                    SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1.0f, 1.0f);

            VxPhysicsWorld world = VxPhysicsWorld.get(player.level().dimension());
            if (world != null) {
                VxToolConfig config = getConfig(player.getUUID());
                float radius = config.getFloat("Chain Radius");
                float segmentLen = config.getFloat("Segment Length");

                world.execute(() -> createChain(world, startHit, endHit, radius, segmentLen));
            }
        });
    }

    /**
     * Constructs the chain of rigid bodies using SwingTwistConstraints.
     * <p>
     * Corrected to use proper vector types:
     * - Positions use RVec3 (Double Precision)
     * - Directions/Axes use Vec3 (Single Precision)
     */
    private void createChain(VxPhysicsWorld world, VxHitResult startHit, VxHitResult endHit, float radius, float desiredSegmentLength) {
        VxBodyManager bodyManager = world.getBodyManager();
        VxConstraintManager constraintManager = world.getConstraintManager();
        BodyInterface bodyInterface = world.getPhysicsSystem().getBodyInterface();

        AttachmentInfo startInfo = getAttachmentInfo(bodyManager, startHit);
        AttachmentInfo endInfo = getAttachmentInfo(bodyManager, endHit);

        if (startInfo.bodyUUID.equals(endInfo.bodyUUID) && !startInfo.bodyUUID.equals(VxConstraintManager.WORLD_BODY_ID)) {
            return;
        }

        VxBody startBody = bodyManager.getVxBody(startInfo.bodyUUID);
        if (startBody != null) bodyInterface.activateBody(startBody.getBodyId());

        VxBody endBody = bodyManager.getVxBody(endInfo.bodyUUID);
        if (endBody != null) bodyInterface.activateBody(endBody.getBodyId());

        RVec3 startPos = startInfo.worldPosition;
        RVec3 endPos = endInfo.worldPosition;

        RVec3 vector = Op.minus(endPos, startPos);
        double distance = vector.length();

        if (distance < desiredSegmentLength) return;

        int numSegments = Math.max(1, (int) Math.ceil(distance / desiredSegmentLength));
        double actualSegmentLength = distance / numSegments;

        // Direction is calculated in high precision (RVec3) for position logic,
        // but converted to Vec3 when used as an orientation axis.
        RVec3 directionR = vector.normalized();
        Vec3 direction = directionR.toVec3();

        Quat orientation = Quat.sFromTo(new Vec3(0, 1, 0), direction);

        // Local axes for constraints (Directions must be Vec3)
        Vec3 localAxisY = new Vec3(0, 1, 0);
        Vec3 localAxisX = new Vec3(1, 0, 0);

        RVec3 segmentVector = Op.star(actualSegmentLength, directionR);

        UUID previousBodyUuid = startInfo.bodyUUID;
        RVec3 pivotOnPreviousBody = startInfo.localPivot;

        for (int i = 0; i < numSegments; ++i) {
            RVec3 segmentStartPos = Op.plus(startPos, Op.star(i, segmentVector));
            RVec3 segmentCenterPos = Op.plus(segmentStartPos, Op.star(0.5, segmentVector));

            VxChainPartRigidBody currentBody = bodyManager.createRigidBody(
                    VxRegisteredBodies.CHAIN_PART,
                    new VxTransform(segmentCenterPos, orientation),
                    body -> {
                        body.setServerData(VxChainPartRigidBody.getLengthAccessor(), (float) actualSegmentLength);
                        body.setServerData(VxChainPartRigidBody.getRadiusAccessor(), radius);
                    }
            );
            if (currentBody == null) continue;

            bodyInterface.activateBody(currentBody.getBodyId());

            RVec3 pivotOnCurrentBodyLocal = new RVec3(0, -actualSegmentLength / 2.0, 0);

            try (SwingTwistConstraintSettings settings = new SwingTwistConstraintSettings()) {
                settings.setNumPositionStepsOverride(30);
                settings.setNumVelocityStepsOverride(30);

                settings.setNormalHalfConeAngle((float) Math.PI);
                settings.setPlaneHalfConeAngle((float) Math.PI);
                settings.setSwingType(com.github.stephengold.joltjni.enumerate.ESwingType.Cone);

                settings.setTwistMinAngle(0.0f);
                settings.setTwistMaxAngle(0.0f);

                if (previousBodyUuid.equals(VxConstraintManager.WORLD_BODY_ID)) {
                    settings.setSpace(EConstraintSpace.WorldSpace);
                    settings.setPosition1(pivotOnPreviousBody);

                    settings.setTwistAxis1(direction); // Vec3 required

                    RMat44 rotationMat = new RMat44();
                    rotationMat.sRotation(orientation);

                    // multiply3x3 returns Vec3 (Rotation of a direction)
                    Vec3 worldAxisX = rotationMat.multiply3x3(localAxisX);
                    settings.setPlaneAxis1(worldAxisX);

                    // For the position calculation, we need to handle types carefully.
                    // Rotate local pivot (Vec3 result) -> Convert to RVec3 -> Add to Center (RVec3)
                    Vec3 rotatedPivot = rotationMat.multiply3x3(pivotOnCurrentBodyLocal.toVec3());
                    RVec3 pivotOnCurrentBodyWorld = Op.plus(segmentCenterPos, new RVec3(rotatedPivot));

                    settings.setPosition2(pivotOnCurrentBodyWorld);
                    settings.setTwistAxis2(direction);
                    settings.setPlaneAxis2(worldAxisX);

                } else {
                    settings.setSpace(EConstraintSpace.LocalToBodyCom);

                    settings.setPosition1(pivotOnPreviousBody);
                    settings.setTwistAxis1(localAxisY);
                    settings.setPlaneAxis1(localAxisX);

                    settings.setPosition2(pivotOnCurrentBodyLocal);
                    settings.setTwistAxis2(localAxisY);
                    settings.setPlaneAxis2(localAxisX);
                }
                constraintManager.createConstraint(settings, previousBodyUuid, currentBody.getPhysicsId());
            }

            previousBodyUuid = currentBody.getPhysicsId();
            pivotOnPreviousBody = new RVec3(0, actualSegmentLength / 2.0, 0);
        }

        // Final constraint to the end point
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
                settings.setSpace(EConstraintSpace.WorldSpace);

                RMat44 lastLinkTransform = bodyInterface.getCenterOfMassTransform(lastLinkBody.getBodyId());
                RVec3 pivotOnLastLinkWorld = lastLinkTransform.multiply3x4(pivotOnPreviousBody);

                // Transform local axes to world axes (Results are Vec3)
                Vec3 axisYWorld = lastLinkTransform.multiply3x3(localAxisY);
                Vec3 axisXWorld = lastLinkTransform.multiply3x3(localAxisX);

                settings.setPosition1(pivotOnLastLinkWorld);
                settings.setTwistAxis1(axisYWorld);
                settings.setPlaneAxis1(axisXWorld);

                settings.setPosition2(endInfo.localPivot);
                settings.setTwistAxis2(axisYWorld);
                settings.setPlaneAxis2(axisXWorld);

            } else {
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

    private AttachmentInfo getAttachmentInfo(VxBodyManager bodyManager, VxHitResult hit) {
        RVec3 worldPosition = new RVec3(hit.getLocation().x, hit.getLocation().y, hit.getLocation().z);

        if (hit.isPhysicsHit()) {
            VxHitResult.PhysicsHit physicsHit = hit.getPhysicsHit().get();
            VxBody hitBody = bodyManager.getByJoltBodyId(physicsHit.bodyId());

            if (hitBody != null && hitBody.getBodyId() != 0) {
                BodyInterface bodyInterface = bodyManager.getPhysicsWorld().getPhysicsSystem().getBodyInterface();
                RMat44 inverseTransform = bodyInterface.getCenterOfMassTransform(hitBody.getBodyId()).inversed();
                RVec3 localPivot = inverseTransform.multiply3x4(worldPosition);
                return new AttachmentInfo(hitBody.getPhysicsId(), worldPosition, localPivot);
            }
        }

        return new AttachmentInfo(VxConstraintManager.WORLD_BODY_ID, worldPosition, worldPosition);
    }

    private Optional<VxHitResult> performRaycast(ServerPlayer player) {
        VxPhysicsWorld world = VxPhysicsWorld.get(player.level().dimension());
        if (world == null) return Optional.empty();

        double reachDistance = player.isCreative() ? 5.0 : 4.5;
        net.minecraft.world.phys.Vec3 from = player.getEyePosition();
        net.minecraft.world.phys.Vec3 look = player.getLookAngle();
        net.minecraft.world.phys.Vec3 to = from.add(look.scale(reachDistance));

        VxClipContext context = new VxClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player, true);
        return VxRaycaster.raycast(player.level(), context);
    }

    private record AttachmentInfo(UUID bodyUUID, RVec3 worldPosition, RVec3 localPivot) {}
}