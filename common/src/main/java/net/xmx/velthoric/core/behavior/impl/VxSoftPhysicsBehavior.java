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
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;

/**
 * The behavior for soft body physics simulation.
 * <p>
 * Bodies with this behavior are simulated using Jolt's soft body dynamics engine,
 * which involves deformable meshes with per-vertex position data.
 *
 * @author xI-Mx-Ix
 */
public class VxSoftPhysicsBehavior implements VxBehavior {

    @Override
    public VxBehaviorId getId() {
        return VxBehaviors.SOFT_PHYSICS;
    }

    @Override
    public void onPrePhysicsTick(VxPhysicsWorld world, VxServerBodyDataStore store) {
        final long mask = VxBehaviors.SOFT_PHYSICS.getMask();
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
        final long mask = VxBehaviors.SOFT_PHYSICS.getMask();
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