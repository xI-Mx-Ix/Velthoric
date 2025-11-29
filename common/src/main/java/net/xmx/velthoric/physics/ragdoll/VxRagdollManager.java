/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.ragdoll;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
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
import org.jetbrains.annotations.Nullable;

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
     * The ragdoll is spawned with the entity's orientation but without any initial linear velocity.
     * This ensures the ragdoll appears "normally" without being launched.
     *
     * @param entity The entity to create a ragdoll from.
     * @param spawnPosition The world position where the ragdoll should be spawned.
     */
    public void createHumanoidRagdoll(LivingEntity entity, RVec3 spawnPosition) {
        // Delegate to internal method with null velocity to indicate a normal spawn
        world.execute(() -> spawnHumanoidRagdollInternal(entity, spawnPosition, null));
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
        world.execute(() -> spawnHumanoidRagdollInternal(entity, spawnPosition, initialVelocity));
    }

    /**
     * Internal method to handle the common logic of spawning a humanoid ragdoll.
     * This handles calculating the correct rotation so the ragdoll faces the direction
     * the entity is looking, creating the body parts, and linking them.
     *
     * @param entity The source entity.
     * @param spawnPosition The spawn location.
     * @param initialVelocity The optional initial velocity (can be null).
     */
    private void spawnHumanoidRagdollInternal(LivingEntity entity, RVec3 spawnPosition, @Nullable Vec3 initialVelocity) {
        // Minecraft Yaw is in degrees and rotates clockwise.
        // Jolt/Math usually expects counter-clockwise rotation in radians.
        // We invert the yaw (-yaw) to align the physics rotation with the visual look direction.
        float rotY = (float) Math.toRadians(-entity.getYRot());
        Quat rotation = Quat.sRotation(new Vec3(0, 1, 0), rotY);

        VxTransform initialTransform = new VxTransform(spawnPosition, rotation);

        String skinId = (entity instanceof Player) ? entity.getUUID().toString() : "";
        Map<VxBodyPart, VxBodyPartRigidBody> parts = new EnumMap<>(VxBodyPart.class);

        // Create all body parts
        for (VxBodyPart partType : VxBodyPart.values()) {
            VxBodyPartRigidBody partBody = createBodyPart(partType, initialTransform, skinId);
            parts.put(partType, partBody);
        }

        // Apply the initial velocity to all created parts if provided
        if (initialVelocity != null && initialVelocity.lengthSq() > 0.0001f) {
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

        // Arms - large swing limits for flailing
        createSwingTwistJoint(torso, parts.get(VxBodyPart.LEFT_ARM), VxBodyPart.LEFT_ARM, 120f, 175f, 175f);
        createSwingTwistJoint(torso, parts.get(VxBodyPart.RIGHT_ARM), VxBodyPart.RIGHT_ARM, 120f, 175f, 175f);

        // Legs
        createSwingTwistJoint(torso, parts.get(VxBodyPart.LEFT_LEG), VxBodyPart.LEFT_LEG, 80f, 140f, 100f);
        createSwingTwistJoint(torso, parts.get(VxBodyPart.RIGHT_LEG), VxBodyPart.RIGHT_LEG, 80f, 140f, 100f);
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
                EActivation.Activate, // Ensure body is active on creation
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
}