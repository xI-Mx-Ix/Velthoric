/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.behavior.impl;

import net.xmx.velthoric.core.behavior.VxBehavior;
import net.xmx.velthoric.core.behavior.VxBehaviorId;
import net.xmx.velthoric.core.body.provider.VxJoltRigidProvider;
import net.xmx.velthoric.init.VxMainClass;

/**
 * The behavior for rigid body physics simulation.
 * <p>
 * Bodies with this behavior are simulated using Jolt's rigid body dynamics engine.
 * This behavior handles pre/post-physics tick callbacks for rigid bodies by iterating
 * the SoA data store and dispatching to bodies that have the {@link #ID} bit set.
 * <p>
 * <b>Note:</b> The actual Jolt body creation is handled by the body's registered
 * {@link VxJoltRigidProvider},
 * which is invoked by the VxServerBodyManager during body construction.
 * This behavior focuses on the per-tick update path.
 *
 * @author xI-Mx-Ix
 */
public class VxRigidPhysicsBehavior implements VxBehavior {

    /**
     * The unique identifier for this behavior.
     * Consumed by the behavior manager for bitmask allocation and dispatch.
     */
    public static final VxBehaviorId ID = new VxBehaviorId(VxMainClass.MODID, "RigidPhysics");

    /**
     * Default constructor for rigid physics behavior.
     */
    public VxRigidPhysicsBehavior() {
    }

    /**
     * Retrieves the unique identifier for this behavior.
     *
     * @return The behavior ID.
     */
    @Override
    public VxBehaviorId getId() {
        return ID;
    }
}