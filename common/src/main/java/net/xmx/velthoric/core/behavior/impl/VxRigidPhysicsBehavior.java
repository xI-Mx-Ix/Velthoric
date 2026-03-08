/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.behavior.impl;

import net.xmx.velthoric.core.behavior.VxBehavior;
import net.xmx.velthoric.core.behavior.VxBehaviorId;
import net.xmx.velthoric.core.behavior.VxBehaviors;
import net.xmx.velthoric.core.body.server.VxServerBodyDataStore;
import net.xmx.velthoric.core.body.VxBody;
import net.xmx.velthoric.core.body.provider.VxJoltRigidProvider;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;

/**
 * The behavior for rigid body physics simulation.
 * <p>
 * Bodies with this behavior are simulated using Jolt's rigid body dynamics engine.
 * This behavior handles pre/post-physics tick callbacks for rigid bodies by iterating
 * the SoA data store and dispatching to bodies that have the {@link VxBehaviors#RIGID_PHYSICS} bit set.
 * <p>
 * <b>Note:</b> The actual Jolt body creation is handled by the body's registered
 * {@link VxJoltRigidProvider},
 * which is invoked by the VxServerBodyManager during body construction.
 * This behavior focuses on the per-tick update path.
 *
 * @author xI-Mx-Ix
 */
public class VxRigidPhysicsBehavior implements VxBehavior {

    @Override
    public VxBehaviorId getId() {
        return VxBehaviors.RIGID_PHYSICS;
    }

    @Override
    public void onPrePhysicsTick(VxPhysicsWorld world, VxServerBodyDataStore store) {
        final long mask = VxBehaviors.RIGID_PHYSICS.getMask();
        final VxBody[] bodies = store.bodies;
        final int capacity = store.getCapacity();

        for (int i = 0; i < capacity; i++) {
            VxBody obj = bodies[i];
            if (obj == null) continue;
            if ((store.behaviorBits[i] & mask) == 0) continue;
            if (!store.isActive[i]) continue;

            obj.onPrePhysicsTick(world);
        }
    }

    @Override
    public void onPhysicsTick(VxPhysicsWorld world, VxServerBodyDataStore store) {
        final long mask = VxBehaviors.RIGID_PHYSICS.getMask();
        final VxBody[] bodies = store.bodies;
        final int capacity = store.getCapacity();

        for (int i = 0; i < capacity; i++) {
            VxBody obj = bodies[i];
            if (obj == null) continue;
            if ((store.behaviorBits[i] & mask) == 0) continue;
            if (!store.isActive[i]) continue;

            obj.onPhysicsTick(world);
        }
    }
}