/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.cd;

import com.github.stephengold.joltjni.ContactManifold;
import com.github.stephengold.joltjni.ContactSettings;
import com.github.stephengold.joltjni.FilteredContactListener;
import com.github.stephengold.joltjni.SubShapeIdPair;
import net.xmx.velthoric.mixin.impl.joltjni.BodyAccessor;
import net.xmx.velthoric.physics.object.manager.VxObjectManager;
import net.xmx.velthoric.physics.object.type.VxBody;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

/**
 * An abstract base class for creating high-performance, customizable contact listeners.
 * <p>
 * End-users should extend this class to implement game logic in response to physics collisions.
 * It provides full access to the filtering capabilities of {@link FilteredContactListener}
 * while abstracting away low-level Jolt details.
 * <p>
 * Instances of this listener are registered with a {@link VxPhysicsWorld}.
 *
 * @author xI-Mx-Ix
 */
public abstract class VxContactListener extends FilteredContactListener {

    private final VxObjectManager objectManager;

    /**
     * Creates a new contact listener.
     *
     * @param objectManager The object manager for the physics world this listener will be added to.
     *                      It is required to resolve body IDs to {@link VxBody} instances.
     */
    protected VxContactListener(VxObjectManager objectManager) {
        if (objectManager == null) {
            throw new IllegalArgumentException("VxObjectManager cannot be null.");
        }
        this.objectManager = objectManager;
    }

    // --- Internal Jolt Event Handling ---
    // These final methods handle the performance-critical translation from native
    // virtual addresses to user-friendly VxBody objects and are not overridable.

    @Override
    public final void onContactAdded(long body1Va, long body2Va, long manifoldVa, long settingsVa) {
        int body1Id = BodyAccessor.velthoric_getId(body1Va);
        int body2Id = BodyAccessor.velthoric_getId(body2Va);

        VxBody vxBody1 = objectManager.getByBodyId(body1Id);
        VxBody vxBody2 = objectManager.getByBodyId(body2Id);

        if (vxBody1 != null && vxBody2 != null) {
            try (ContactManifold manifold = new ContactManifold(manifoldVa);
                 ContactSettings settings = new ContactSettings(settingsVa)) {
                onContactAdded(vxBody1, vxBody2, manifold, settings);
            }
        }
    }

    @Override
    public final void onContactPersisted(long body1Va, long body2Va, long manifoldVa, long settingsVa) {
        int body1Id = BodyAccessor.velthoric_getId(body1Va);
        int body2Id = BodyAccessor.velthoric_getId(body2Va);

        VxBody vxBody1 = objectManager.getByBodyId(body1Id);
        VxBody vxBody2 = objectManager.getByBodyId(body2Id);

        if (vxBody1 != null && vxBody2 != null) {
            try (ContactManifold manifold = new ContactManifold(manifoldVa);
                 ContactSettings settings = new ContactSettings(settingsVa)) {
                onContactPersisted(vxBody1, vxBody2, manifold, settings);
            }
        }
    }

    @Override
    public final void onContactRemoved(long pairVa) {
        try (SubShapeIdPair pair = new SubShapeIdPair(pairVa)) {
            VxBody vxBody1 = objectManager.getByBodyId(pair.getBody1Id());
            VxBody vxBody2 = objectManager.getByBodyId(pair.getBody2Id());

            if (vxBody1 != null && vxBody2 != null) {
                onContactRemoved(vxBody1, vxBody2, pair);
            }
        }
    }

    // --- User-Facing Callbacks ---

    /**
     * Called on the physics thread when a new contact is made between two bodies, after all filters have passed.
     * <p>
     * This is the primary method to implement for collision-based game logic.
     *
     * @param body1    The first body in the collision.
     * @param body2    The second body in the collision.
     * @param manifold Provides detailed information about the collision point, such as penetration depth and the contact normal.
     * @param settings Allows modification of contact properties like friction and restitution for this specific collision.
     */
    public abstract void onContactAdded(VxBody body1, VxBody body2, ContactManifold manifold, ContactSettings settings);

    /**
     * Called on the physics thread for a contact that persists from the previous physics step.
     * <p>
     * This is useful for continuous effects, like an object taking damage from acid.
     * Override this method if needed; it is optional.
     *
     * @param body1    The first body in the collision.
     * @param body2    The second body in the collision.
     * @param manifold The current state of the contact manifold.
     * @param settings The contact settings for this persistent contact.
     */
    public void onContactPersisted(VxBody body1, VxBody body2, ContactManifold manifold, ContactSettings settings) {
    }

    /**
     * Called on the physics thread when two bodies that were previously in contact are now separated.
     * <p>
     * Override this method if needed; it is optional.
     *
     * @param body1 The first body that is no longer in contact.
     * @param body2 The second body that is no longer in contact.
     * @param pair  Contains the sub-shape IDs of the contact that was lost.
     */
    public void onContactRemoved(VxBody body1, VxBody body2, SubShapeIdPair pair) {
    }
}