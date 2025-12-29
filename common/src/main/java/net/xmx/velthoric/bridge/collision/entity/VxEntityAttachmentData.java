/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.bridge.collision.entity;

import com.github.stephengold.joltjni.RMat44;
import com.github.stephengold.joltjni.Vec3;

import java.util.UUID;

/**
 * A data container holding the state of an entity's attachment to a physics body.
 * Used to calculate relative movement when an entity is standing on a moving object.
 *
 * @author xI-Mx-Ix
 */
public final class VxEntityAttachmentData {
    /**
     * The UUID of the physics body the entity is currently attached to.
     */
    public UUID attachedBodyUuid = null;

    /**
     * The world transform of the attached body during the previous tick.
     * Used to calculate the delta movement.
     */
    public RMat44 lastBodyTransform = null;

    /**
     * Counter for how long the entity has been airborne.
     * Used to provide a grace period before detaching.
     */
    public int ticksSinceGrounded = 0;

    /**
     * The normal vector of the ground the entity was last standing on.
     */
    public Vec3 lastGroundNormal = new Vec3(0f, 1f, 0f);

    /**
     * The amount of position offset applied to the entity in the last tick due to body movement.
     */
    public Vec3 addedMovementLastTick = new Vec3(0f, 0f, 0f);

    /**
     * The amount of yaw rotation applied to the entity in the last tick due to body rotation.
     */
    public float addedYawRotLastTick = 0.0f;

    /**
     * Checks if the entity is currently considered attached to a physics body.
     *
     * @return True if attached and within the grounded grace period.
     */
    public boolean isAttached() {
        return attachedBodyUuid != null && ticksSinceGrounded < 5;
    }

    /**
     * Clears the attachment state and releases native resources.
     */
    public void detach() {
        this.attachedBodyUuid = null;
        if (this.lastBodyTransform != null) {
            this.lastBodyTransform.close();
            this.lastBodyTransform = null;
        }
    }
}