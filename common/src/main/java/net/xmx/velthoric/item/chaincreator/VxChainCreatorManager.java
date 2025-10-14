/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.chaincreator;

import com.github.stephengold.joltjni.PointConstraintSettings;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RMat44;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.operator.Op;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.xmx.velthoric.builtin.VxRegisteredBodies;
import net.xmx.velthoric.item.chaincreator.body.VxChainPartRigidBody;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.physics.body.manager.VxBodyManager;
import net.xmx.velthoric.physics.body.type.VxBody;
import net.xmx.velthoric.physics.constraint.manager.VxConstraintManager;
import net.xmx.velthoric.physics.raycasting.VxClipContext;
import net.xmx.velthoric.physics.raycasting.VxHitResult;
import net.xmx.velthoric.physics.raycasting.VxRaycaster;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the creation of physics-based chains initiated by players.
 * This singleton class handles the server-side logic for the Chain Creator item.
 *
 * @author xI-Mx-Ix
 */
public enum VxChainCreatorManager {
    INSTANCE;

    private final Map<UUID, VxHitResult> chainCreators = new ConcurrentHashMap<>();
    private VxPhysicsWorld world;

    /**
     * Initiates the chain creation process for a player.
     * Performs a raycast to find the first attachment point and stores it.
     * @param player The player starting the chain creation.
     */
    public void startChainCreation(ServerPlayer player) {
        performRaycast(player).ifPresent(hitResult ->
                chainCreators.put(player.getUUID(), hitResult)
        );
    }

    /**
     * Completes the chain creation process.
     * Performs a second raycast to find the end point and then constructs the chain.
     * @param player The player finishing the chain creation.
     */
    public void finishChainCreation(ServerPlayer player) {
        VxHitResult startHit = chainCreators.remove(player.getUUID());
        if (startHit == null) return;

        performRaycast(player).ifPresent(endHit ->
                world.execute(() -> createChain(startHit, endHit))
        );
    }

    /**
     * Constructs the chain of rigid bodies and constraints.
     * This method now correctly handles the coordinate spaces for pivot points
     * to prevent physics instabilities.
     *
     * @param startHit The raycast result for the starting attachment point.
     * @param endHit The raycast result for the ending attachment point.
     */
    private void createChain(VxHitResult startHit, VxHitResult endHit) {
        VxPhysicsWorld world = this.world;
        if (world == null) return;

        VxBodyManager bodyManager = world.getBodyManager();
        VxConstraintManager constraintManager = world.getConstraintManager();
        BodyInterface bodyInterface = world.getPhysicsSystem().getBodyInterface();

        AttachmentInfo startInfo = getAttachmentInfo(bodyManager, startHit);
        AttachmentInfo endInfo = getAttachmentInfo(bodyManager, endHit);
        if (startInfo == null || endInfo == null || startInfo.bodyUUID.equals(endInfo.bodyUUID)) {
            return;
        }

        // Activate attachment bodies if they are dynamic
        VxBody startBody = bodyManager.getVxBody(startInfo.bodyUUID);
        if (startBody != null) {
            bodyInterface.activateBody(startBody.getBodyId());
        }
        VxBody endBody = bodyManager.getVxBody(endInfo.bodyUUID);
        if (endBody != null) {
            bodyInterface.activateBody(endBody.getBodyId());
        }

        RVec3 startPos = startInfo.worldPosition;
        RVec3 endPos = endInfo.worldPosition;

        final double chainPartRadius = 0.1;
        final double desiredSegmentLength = 0.5;
        RVec3 vector = Op.minus(endPos, startPos);
        double distance = vector.length();
        if (distance < desiredSegmentLength) return;

        int numSegments = Math.max(1, (int) Math.ceil(distance / desiredSegmentLength));
        double actualSegmentLength = distance / numSegments;
        RVec3 direction = vector.normalized();
        Quat orientation = Quat.sFromTo(new Vec3(0, 1, 0), direction.toVec3());
        RVec3 segmentVector = Op.star(actualSegmentLength, direction);

        UUID previousBodyUuid = startInfo.bodyUUID;
        RVec3 pivotOnPreviousBody = startInfo.localPivot;

        for (int i = 0; i < numSegments; ++i) {
            RVec3 segmentStartPos = Op.plus(startPos, Op.star(i, segmentVector));
            RVec3 segmentCenterPos = Op.plus(segmentStartPos, Op.star(0.5, segmentVector));

            VxChainPartRigidBody currentBody = bodyManager.createRigidBody(
                    VxRegisteredBodies.CHAIN_PART, new VxTransform(segmentCenterPos, orientation), body -> {
                        body.setSyncData(VxChainPartRigidBody.getLengthAccessor(), (float) actualSegmentLength);
                        body.setSyncData(VxChainPartRigidBody.getRadiusAccessor(), (float) chainPartRadius);
                    }
            );
            if (currentBody == null) continue;

            // Activate the newly created chain part
            bodyInterface.activateBody(currentBody.getBodyId());

            RVec3 pivotOnCurrentBodyLocal = new RVec3(0, -actualSegmentLength / 2.0, 0);

            try (PointConstraintSettings settings = new PointConstraintSettings()) {
                if (previousBodyUuid.equals(VxConstraintManager.WORLD_BODY_ID)) {
                    // Attaching WORLD to currentBody. Use WorldSpace.
                    settings.setSpace(EConstraintSpace.WorldSpace);
                    // Point1 is the attachment on the world (already in world space).
                    settings.setPoint1(pivotOnPreviousBody);

                    // To get Point2, rotate the local pivot by the body's orientation and add its world position.
                    RMat44 segmentRotation = new RMat44();
                    segmentRotation.sRotation(orientation); // Create a pure rotation matrix from the quaternion.
                    Vec3 rotatedPivot = segmentRotation.multiply3x3(pivotOnCurrentBodyLocal.toVec3());
                    RVec3 pivotOnCurrentBodyWorld = Op.plus(segmentCenterPos, rotatedPivot);
                    settings.setPoint2(pivotOnCurrentBodyWorld);
                } else {
                    // Attaching previousBody to currentBody. Use LocalToBodyCom.
                    settings.setSpace(EConstraintSpace.LocalToBodyCom);
                    settings.setPoint1(pivotOnPreviousBody);
                    settings.setPoint2(pivotOnCurrentBodyLocal);
                }
                constraintManager.createConstraint(settings, previousBodyUuid, currentBody.getPhysicsId());
            }

            previousBodyUuid = currentBody.getPhysicsId();
            pivotOnPreviousBody = new RVec3(0, actualSegmentLength / 2.0, 0);
        }

        // Create the final constraint from the last chain link to the end point.
        try (PointConstraintSettings settings = new PointConstraintSettings()) {
            VxBody lastLinkBody = bodyManager.getVxBody(previousBodyUuid);
            if (lastLinkBody == null) return; // Should not happen if loop ran.

            if (endInfo.bodyUUID.equals(VxConstraintManager.WORLD_BODY_ID)) {
                // Attaching last link to WORLD. Use WorldSpace.
                settings.setSpace(EConstraintSpace.WorldSpace);

                // Point1: Convert the local pivot on the last link to world space.
                RMat44 lastLinkTransform = world.getPhysicsSystem().getBodyInterface().getCenterOfMassTransform(lastLinkBody.getBodyId());
                RVec3 pivotOnLastLinkWorld = lastLinkTransform.multiply3x4(pivotOnPreviousBody);
                settings.setPoint1(pivotOnLastLinkWorld);

                // Point2: The attachment on the world (already in world space).
                settings.setPoint2(endInfo.localPivot);
            } else {
                // Attaching last link to endBody. Use LocalToBodyCom.
                settings.setSpace(EConstraintSpace.LocalToBodyCom);
                settings.setPoint1(pivotOnPreviousBody);
                settings.setPoint2(endInfo.localPivot);
            }
            constraintManager.createConstraint(settings, previousBodyUuid, endInfo.bodyUUID);
        }
    }

    /**
     * Analyzes a hit result to determine attachment information.
     * If the hit is on a physics body, it returns information to attach to that body in its local space.
     * If the hit is on a non-physics block, it returns information to attach to the static world
     * at that point using a special world UUID.
     *
     * @param bodyManager The body manager.
     * @param hit         The raycast hit result.
     * @return An AttachmentInfo record, or null if the attachment is invalid.
     */
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

        // Fallback for non-physics hits: attach to the world.
        // The pivot point for a world attachment is its position in world space.
        return new AttachmentInfo(VxConstraintManager.WORLD_BODY_ID, worldPosition, worldPosition);
    }

    /**
     * Performs a raycast using the VxRaycaster utility.
     * @param player The player to cast from.
     * @return An Optional containing the hit result.
     */
    private Optional<VxHitResult> performRaycast(ServerPlayer player) {
        VxPhysicsWorld world = VxPhysicsWorld.get(player.level().dimension());
        if (world == null) return Optional.empty();
        this.world = world;

        net.minecraft.world.phys.Vec3 from = player.getEyePosition();
        net.minecraft.world.phys.Vec3 look = player.getLookAngle();
        net.minecraft.world.phys.Vec3 to = from.add(look.scale(100.0));

        VxClipContext context = new VxClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player, true);
        return VxRaycaster.raycast(player.level(), context);
    }

    /**
     * Clears pending data for a player who has disconnected.
     * @param player The player who is quitting.
     */
    public void onPlayerQuit(ServerPlayer player) {
        chainCreators.remove(player.getUUID());
    }

    /**
     * A helper record to store attachment information.
     * @param bodyUUID The UUID of the body to attach to. For world attachments, this will be the Nil-UUID.
     * @param worldPosition The world-space position of the attachment.
     * @param localPivot The pivot point. For dynamic bodies, this is in local space. For world attachments, this is in world space.
     */
    private record AttachmentInfo(UUID bodyUUID, RVec3 worldPosition, RVec3 localPivot) {}
}