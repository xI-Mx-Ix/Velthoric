/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.ragdoll;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.enumerate.ESwingType;
import com.github.stephengold.joltjni.operator.Op;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.xmx.velthoric.builtin.VxRegisteredBodies;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.physics.body.manager.VxBodyManager;
import net.xmx.velthoric.physics.constraint.manager.VxConstraintManager;
import net.xmx.velthoric.physics.ragdoll.body.VxBodyPartRigidBody;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.EnumMap;
import java.util.Map;

/**
 * Manages the creation and lifecycle of ragdolls within a physics world.
 * This class handles spawning the individual rigid bodies for each body part
 * and connecting them with appropriate constraints to simulate ragdoll physics.
 *
 * @author xI-Mx-Ix
 */
public class VxRagdollManager {

    private final VxPhysicsWorld world;
    private final VxBodyManager bodyManager;
    private final VxConstraintManager constraintManager;

    public VxRagdollManager(VxPhysicsWorld world) {
        this.world = world;
        this.bodyManager = world.getBodyManager();
        this.constraintManager = world.getConstraintManager();
    }

    /**
     * Creates a humanoid ragdoll based on the state of a living entity at a specific world position.
     * This method queues the creation logic to be executed on the physics thread.
     *
     * @param entity The entity to create a ragdoll from.
     * @param spawnPosition The world position where the ragdoll should be spawned.
     */
    public void createHumanoidRagdoll(LivingEntity entity, RVec3 spawnPosition) {
        world.execute(() -> {
            VxTransform initialTransform = new VxTransform(
                    spawnPosition,
                    Quat.sEulerAngles(0, entity.getYRot() * ((float)Math.PI / 180f), 0)
            );

            String skinId = (entity instanceof Player) ? entity.getUUID().toString() : "";

            Map<VxBodyPart, VxBodyPartRigidBody> parts = new EnumMap<>(VxBodyPart.class);

            for (VxBodyPart partType : VxBodyPart.values()) {
                VxBodyPartRigidBody partBody = createBodyPart(partType, initialTransform, skinId);
                parts.put(partType, partBody);
            }

            VxBodyPartRigidBody torso = parts.get(VxBodyPart.TORSO);
            if (torso == null) return;

            // Increased joint limits for a much looser, more "floppy" ragdoll behavior.

            // Connect Head to Torso with much looser constraints.
            createSwingTwistJoint(torso, parts.get(VxBodyPart.HEAD), VxBodyPart.HEAD, 80f, 120f, 80f);

            // Arms are given very large swing limits to allow them to flail freely.
            createSwingTwistJoint(torso, parts.get(VxBodyPart.LEFT_ARM), VxBodyPart.LEFT_ARM, 120f, 175f, 175f);
            createSwingTwistJoint(torso, parts.get(VxBodyPart.RIGHT_ARM), VxBodyPart.RIGHT_ARM, 120f, 175f, 175f);

            // Legs are also made much looser.
            createSwingTwistJoint(torso, parts.get(VxBodyPart.LEFT_LEG), VxBodyPart.LEFT_LEG, 80f, 140f, 100f);
            createSwingTwistJoint(torso, parts.get(VxBodyPart.RIGHT_LEG), VxBodyPart.RIGHT_LEG, 80f, 140f, 100f);
        });
    }

    private VxBodyPartRigidBody createBodyPart(VxBodyPart partType, VxTransform initialTransform, String skinId) {
        Vec3 size = partType.getSize();
        Vec3 halfExtents = Op.star(0.5f, size);

        // Calculate initial position relative to the main transform
        Vec3 partOffsetVec = partType.getAttachmentPointOnTorso();
        RVec3 partOffset = partOffsetVec.toRVec3();

        // The body part's own center is offset from its joint location.
        // For example, the arm's center is halfway down its length, so we shift it down from the joint.
        RVec3 pivotOffset = Op.minus(partType.getLocalPivot().toRVec3());

        // Combine offsets and rotate them by the initial orientation
        RVec3 finalOffset = Op.plus(partOffset, pivotOffset);
        finalOffset.rotateInPlace(initialTransform.getRotation());

        RVec3 partPosition = Op.plus(initialTransform.getTranslation(), finalOffset);
        VxTransform partTransform = new VxTransform(partPosition, initialTransform.getRotation());

        return bodyManager.createRigidBody(
                VxRegisteredBodies.BODY_PART,
                partTransform,
                body -> {
                    body.setSyncData(VxBodyPartRigidBody.DATA_HALF_EXTENTS, halfExtents);
                    body.setSyncData(VxBodyPartRigidBody.DATA_BODY_PART, partType);
                    body.setSyncData(VxBodyPartRigidBody.DATA_SKIN_ID, skinId);
                }
        );
    }

    private void createSwingTwistJoint(VxBodyPartRigidBody torso, VxBodyPartRigidBody limb, VxBodyPart limbType, float twist, float swingY, float swingZ) {
        if (torso == null || limb == null) return;

        try (SwingTwistConstraintSettings settings = new SwingTwistConstraintSettings()) {
            settings.setSpace(EConstraintSpace.LocalToBodyCom);
            settings.setSwingType(ESwingType.Pyramid);

            // Set pivot points in local space of each body
            Vec3 pivotOnTorso = limbType.getAttachmentPointOnTorso();
            settings.setPosition1(pivotOnTorso.toRVec3());

            Vec3 pivotOnLimb = limbType.getLocalPivot();
            settings.setPosition2(pivotOnLimb.toRVec3());

            // Explicitly define the joint axes to prevent incorrect rotation.
            // The primary axis (twist) is Y (up), the secondary axis (plane) is X (forward).
            Vec3 primaryAxis = new Vec3(0, 1, 0);
            Vec3 secondaryAxis = new Vec3(1, 0, 0);
            settings.setTwistAxis1(primaryAxis);
            settings.setPlaneAxis1(secondaryAxis);
            settings.setTwistAxis2(primaryAxis);
            settings.setPlaneAxis2(secondaryAxis);

            // Convert degrees to radians for Jolt
            settings.setTwistMinAngle((float) Math.toRadians(-twist));
            settings.setTwistMaxAngle((float) Math.toRadians(twist));
            settings.setPlaneHalfConeAngle((float) Math.toRadians(swingY));
            settings.setNormalHalfConeAngle((float) Math.toRadians(swingZ));

            constraintManager.createConstraint(settings, torso.getPhysicsId(), limb.getPhysicsId());
        }
    }

    /**
     * Creates a humanoid ragdoll and applies an initial linear velocity to all its parts.
     * Useful for launching ragdolls from items or explosions.
     *
     * @param entity The entity to create a ragdoll from.
     * @param spawnPosition The world position where the ragdoll should be spawned.
     * @param initialVelocity The initial velocity to apply to every body part.
     */
    public void launchHumanoidRagdoll(LivingEntity entity, RVec3 spawnPosition, Vec3 initialVelocity) {
        world.execute(() -> {
            VxTransform initialTransform = new VxTransform(
                    spawnPosition,
                    Quat.sEulerAngles(0, entity.getYRot() * ((float)Math.PI / 180f), 0)
            );

            String skinId = (entity instanceof Player) ? entity.getUUID().toString() : "";
            Map<VxBodyPart, VxBodyPartRigidBody> parts = new EnumMap<>(VxBodyPart.class);

            // Create all body parts
            for (VxBodyPart partType : VxBodyPart.values()) {
                VxBodyPartRigidBody partBody = createBodyPart(partType, initialTransform, skinId);
                parts.put(partType, partBody);
            }

            // Apply the initial velocity to all created parts
            if (initialVelocity.lengthSq() > 0.0001f) {
                BodyInterface bodyInterface = world.getPhysicsSystem().getBodyInterface();
                for (VxBodyPartRigidBody part : parts.values()) {
                    if (part != null) {
                        bodyInterface.setLinearVelocity(part.getBodyId(), initialVelocity);
                        bodyInterface.activateBody(part.getBodyId());
                    }
                }
            }

            VxBodyPartRigidBody torso = parts.get(VxBodyPart.TORSO);
            if (torso == null) return;

            // Connect joints with specific limits for ragdoll movement
            createSwingTwistJoint(torso, parts.get(VxBodyPart.HEAD), VxBodyPart.HEAD, 80f, 120f, 80f);

            // Arms
            createSwingTwistJoint(torso, parts.get(VxBodyPart.LEFT_ARM), VxBodyPart.LEFT_ARM, 120f, 175f, 175f);
            createSwingTwistJoint(torso, parts.get(VxBodyPart.RIGHT_ARM), VxBodyPart.RIGHT_ARM, 120f, 175f, 175f);

            // Legs
            createSwingTwistJoint(torso, parts.get(VxBodyPart.LEFT_LEG), VxBodyPart.LEFT_LEG, 80f, 140f, 100f);
            createSwingTwistJoint(torso, parts.get(VxBodyPart.RIGHT_LEG), VxBodyPart.RIGHT_LEG, 80f, 140f, 100f);
        });
    }

}