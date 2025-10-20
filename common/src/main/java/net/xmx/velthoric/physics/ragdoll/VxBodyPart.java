/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.ragdoll;

import com.github.stephengold.joltjni.Vec3;

/**
 * Defines the standard parts of a humanoid ragdoll, including their
 * dimensions and pivot points for joint creation. All dimensions are
 * in meters and relative to the part's center.
 *
 * @author xI-Mx-Ix
 */
public enum VxBodyPart {
    HEAD(new Vec3(0.5f, 0.5f, 0.5f), new Vec3(0.0f, 0.375f, 0.0f)),
    TORSO(new Vec3(0.5f, 0.75f, 0.25f), new Vec3(0.0f, 0.0f, 0.0f)),

    LEFT_ARM(new Vec3(0.25f, 0.75f, 0.25f), new Vec3( 0.25f, 0.375f, 0.0f)),
    RIGHT_ARM(new Vec3(0.25f, 0.75f, 0.25f), new Vec3(-0.25f, 0.375f, 0.0f)),

    LEFT_LEG(new Vec3(0.25f, 0.75f, 0.25f), new Vec3( 0.125f, -0.375f, 0.0f)),
    RIGHT_LEG(new Vec3(0.25f, 0.75f, 0.25f), new Vec3(-0.125f, -0.375f, 0.0f));

    private final Vec3 size;
    private final Vec3 attachmentPointOnTorso;

    /**
     * Defines a body part for a ragdoll.
     *
     * @param size The full size (width, height, depth) of the physics shape for this part.
     * @param attachmentPointOnTorso The local position on the torso where this part's joint is attached.
     */
    VxBodyPart(Vec3 size, Vec3 attachmentPointOnTorso) {
        this.size = size;
        this.attachmentPointOnTorso = attachmentPointOnTorso;
    }

    /**
     * Gets the full size of this body part.
     *
     * @return A vector representing the width, height, and depth.
     */
    public Vec3 getSize() {
        return size;
    }

    /**
     * Gets the attachment point for this part on the torso, in the torso's local space.
     * For the torso itself, this is its center.
     *
     * @return A vector representing the local attachment point.
     */
    public Vec3 getAttachmentPointOnTorso() {
        return attachmentPointOnTorso;
    }

    /**
     * Calculates the local pivot point on this body part for its joint connection.
     * This is typically at the top-center for limbs and the bottom-center for the head.
     *
     * @return A vector representing the local pivot point.
     */
    public Vec3 getLocalPivot() {
        if (this == TORSO) {
            return new Vec3(0, 0, 0); // Torso's pivot is its center.
        }
        // For the head, the pivot is at the bottom-center (neck joint).
        if (this == HEAD) {
            return new Vec3(0.0f, -size.getY() / 2.0f, 0.0f);
        }

        // For arms, the pivot is at the top-inner corner (shoulder joint).
        if (this == LEFT_ARM) {
            // Move pivot to the inner side (negative X) of the arm.
            return new Vec3(-size.getX() / 2.0f, size.getY() / 2.0f, 0.0f);
        }
        if (this == RIGHT_ARM) {
            // Move pivot to the inner side (positive X) of the arm.
            return new Vec3(size.getX() / 2.0f, size.getY() / 2.0f, 0.0f);
        }

        // For other limbs (legs), the joint remains at the top-center.
        return new Vec3(0.0f, size.getY() / 2.0f, 0.0f);
    }
}