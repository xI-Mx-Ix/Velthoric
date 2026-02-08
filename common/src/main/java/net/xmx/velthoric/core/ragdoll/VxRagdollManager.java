/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.ragdoll;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.enumerate.ESwingType;
import com.github.stephengold.joltjni.operator.Op;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.xmx.velthoric.builtin.VxRegisteredBodies;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.core.body.manager.VxBodyManager;
import net.xmx.velthoric.core.constraint.manager.VxConstraintManager;
import net.xmx.velthoric.core.ragdoll.body.VxBodyPartRigidBody;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;
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
     * The ragdoll is spawned with the entity's Yaw orientation but remains upright (no Pitch),
     * suitable for spawning a dead body or a statue.
     *
     * @param entity        The entity to create a ragdoll from.
     * @param spawnPosition The world position where the ragdoll should be spawned.
     */
    public void createHumanoidRagdoll(LivingEntity entity, RVec3 spawnPosition) {
        // Pass null for velocity to indicate a static/standing spawn
        world.execute(() -> spawnHumanoidRagdollInternal(entity, spawnPosition, null));
    }

    /**
     * Creates a humanoid ragdoll and applies an initial linear velocity to all its parts.
     * The ragdoll is oriented to match the entity's full look direction (Yaw and Pitch),
     * making it suitable for projectile-like launching.
     *
     * @param entity          The entity to create a ragdoll from.
     * @param spawnPosition   The world position where the ragdoll should be spawned.
     * @param initialVelocity The initial velocity to apply to every body part.
     */
    public void launchHumanoidRagdoll(LivingEntity entity, RVec3 spawnPosition, Vec3 initialVelocity) {
        world.execute(() -> spawnHumanoidRagdollInternal(entity, spawnPosition, initialVelocity));
    }

    /**
     * Internal method to handle the creation, orientation, and linking of ragdoll parts.
     *
     * @param entity          The source entity.
     * @param spawnPosition   The spawn location.
     * @param initialVelocity The optional initial velocity. If provided, the ragdoll includes Pitch in its rotation.
     */
    private void spawnHumanoidRagdollInternal(LivingEntity entity, RVec3 spawnPosition, @Nullable Vec3 initialVelocity) {
        // Calculate Rotation
        // Minecraft Yaw rotates clockwise in degrees. Invert for standard counter-clockwise math.
        float rotY = (float) Math.toRadians(-entity.getYRot());
        Quat yawRotation = Quat.sRotation(new Vec3(0, 1, 0), rotY);

        Quat finalRotation;

        if (initialVelocity != null) {
            float rotX = (float) Math.toRadians(entity.getXRot());

            Quat pitchRotation = Quat.sRotation(new Vec3(1, 0, 0), rotX);

            // Multiply rotations using Op.star (Yaw * Pitch)
            finalRotation = Op.star(yawRotation, pitchRotation);
        } else {
            finalRotation = yawRotation;
        }

        VxTransform initialTransform = new VxTransform(spawnPosition, finalRotation);

        String skinId = (entity instanceof Player) ? entity.getUUID().toString() : "";
        Map<VxBodyPart, VxBodyPartRigidBody> parts = new EnumMap<>(VxBodyPart.class);

        // Generate rigid bodies for all defined body parts based on the calculated transform
        for (VxBodyPart partType : VxBodyPart.values()) {
            VxBodyPartRigidBody partBody = createBodyPart(partType, initialTransform, skinId);
            parts.put(partType, partBody);
        }

        // Apply velocity if requested
        if (initialVelocity != null && initialVelocity.lengthSq() > 0.0001f) {
            BodyInterface bodyInterface = world.getPhysicsSystem().getBodyInterface();
            for (VxBodyPartRigidBody part : parts.values()) {
                if (part != null) {
                    bodyInterface.setLinearVelocity(part.getBodyId(), initialVelocity);
                    bodyInterface.activateBody(part.getBodyId());
                }
            }
        }

        // Link parts with constraints
        VxBodyPartRigidBody torso = parts.get(VxBodyPart.TORSO);
        if (torso == null) return;

        // Neck
        createSwingTwistJoint(torso, parts.get(VxBodyPart.HEAD), VxBodyPart.HEAD, 80f, 120f, 80f);

        // Arms - High freedom of movement
        createSwingTwistJoint(torso, parts.get(VxBodyPart.LEFT_ARM), VxBodyPart.LEFT_ARM, 120f, 175f, 175f);
        createSwingTwistJoint(torso, parts.get(VxBodyPart.RIGHT_ARM), VxBodyPart.RIGHT_ARM, 120f, 175f, 175f);

        // Legs
        createSwingTwistJoint(torso, parts.get(VxBodyPart.LEFT_LEG), VxBodyPart.LEFT_LEG, 80f, 140f, 100f);
        createSwingTwistJoint(torso, parts.get(VxBodyPart.RIGHT_LEG), VxBodyPart.RIGHT_LEG, 80f, 140f, 100f);
    }

    /**
     * Creates a single rigid body for a specific body part, calculating its position relative
     * to the ragdoll's root transform.
     */
    private VxBodyPartRigidBody createBodyPart(VxBodyPart partType, VxTransform initialTransform, String skinId) {
        Vec3 size = partType.getSize();
        Vec3 halfExtents = Op.star(0.5f, size);

        // Determine the offset of this part relative to the Torso (0,0,0)
        Vec3 partOffsetVec = partType.getAttachmentPointOnTorso();
        RVec3 partOffset = partOffsetVec.toRVec3();

        // Adjust for the part's pivot point (center of mass offset)
        RVec3 pivotOffset = Op.minus(partType.getLocalPivot().toRVec3());

        // Combine offsets and rotate them into world space using the ragdoll's global orientation
        RVec3 finalOffset = Op.plus(partOffset, pivotOffset);
        finalOffset.rotateInPlace(initialTransform.getRotation());

        RVec3 partPosition = Op.plus(initialTransform.getTranslation(), finalOffset);

        // The individual part inherits the global rotation (including pitch if launched)
        VxTransform partTransform = new VxTransform(partPosition, initialTransform.getRotation());

        return bodyManager.createRigidBody(
                VxRegisteredBodies.BODY_PART,
                partTransform,
                EActivation.Activate,
                body -> {
                    body.setServerData(VxBodyPartRigidBody.DATA_HALF_EXTENTS, halfExtents);
                    body.setServerData(VxBodyPartRigidBody.DATA_BODY_PART, partType);
                    body.setServerData(VxBodyPartRigidBody.DATA_SKIN_ID, skinId);
                }
        );
    }

    private void createSwingTwistJoint(VxBodyPartRigidBody torso, VxBodyPartRigidBody limb, VxBodyPart limbType, float twist, float swingY, float swingZ) {
        if (torso == null || limb == null) return;

        try (SwingTwistConstraintSettings settings = new SwingTwistConstraintSettings()) {
            settings.setSpace(EConstraintSpace.LocalToBodyCom);
            settings.setSwingType(ESwingType.Pyramid);

            // Pivot points are defined in the local space of the respective bodies
            Vec3 pivotOnTorso = limbType.getAttachmentPointOnTorso();
            settings.setPosition1(pivotOnTorso.toRVec3());

            Vec3 pivotOnLimb = limbType.getLocalPivot();
            settings.setPosition2(pivotOnLimb.toRVec3());

            // Define the primary axes for the joint constraints (Up and Right relative to the body part)
            Vec3 primaryAxis = new Vec3(0, 1, 0);
            Vec3 secondaryAxis = new Vec3(1, 0, 0);
            settings.setTwistAxis1(primaryAxis);
            settings.setPlaneAxis1(secondaryAxis);
            settings.setTwistAxis2(primaryAxis);
            settings.setPlaneAxis2(secondaryAxis);

            settings.setTwistMinAngle((float) Math.toRadians(-twist));
            settings.setTwistMaxAngle((float) Math.toRadians(twist));
            settings.setPlaneHalfConeAngle((float) Math.toRadians(swingY));
            settings.setNormalHalfConeAngle((float) Math.toRadians(swingZ));

            constraintManager.createConstraint(settings, torso.getPhysicsId(), limb.getPhysicsId());
        }
    }
}