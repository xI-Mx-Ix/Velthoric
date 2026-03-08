/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.behavior.impl;

import net.minecraft.server.level.ServerLevel;
import net.xmx.velthoric.core.behavior.VxBehavior;
import net.xmx.velthoric.core.behavior.VxBehaviorId;
import net.xmx.velthoric.core.behavior.VxBehaviors;
import net.xmx.velthoric.core.body.server.VxServerBodyDataStore;
import net.xmx.velthoric.core.body.type.VxBody;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;

/**
 * A combined behavior handling all ticking callbacks.
 * <p>
 * Bodies with this behavior attached will have their tick methods invoked:
 * <ul>
 *   <li>{@link VxBody#onPrePhysicsTick(VxPhysicsWorld)} - Before the Jolt simulation step</li>
 *   <li>{@link VxBody#onPhysicsTick(VxPhysicsWorld)} - After the Jolt simulation step</li>
 *   <li>{@link VxBody#onServerTick(ServerLevel)} - Once per game tick on the Minecraft server thread</li>
 * </ul>
 * <p>
 * This consolidated behavior avoids the overhead of managing three separate bits 
 * for typical ticking objects.
 *
 * @author xI-Mx-Ix
 */
public class VxTickBehavior implements VxBehavior {

    @Override
    public VxBehaviorId getId() {
        return VxBehaviors.TICK;
    }

    @Override
    public void onServerTick(ServerLevel level, VxServerBodyDataStore store) {
        final long mask = VxBehaviors.TICK.getMask();
        final VxBody[] bodies = store.bodies;
        final int capacity = store.getCapacity();

        for (int i = 0; i < capacity; i++) {
            VxBody obj = bodies[i];
            if (obj == null) continue;
            if ((store.behaviorBits[i] & mask) == 0) continue;

            obj.onServerTick(level);
        }
    }

    @Override
    public void onPrePhysicsTick(VxPhysicsWorld world, VxServerBodyDataStore store) {
        final long mask = VxBehaviors.TICK.getMask();
        final VxBody[] bodies = store.bodies;
        final int capacity = store.getCapacity();

        for (int i = 0; i < capacity; i++) {
            VxBody obj = bodies[i];
            if (obj == null) continue;
            if ((store.behaviorBits[i] & mask) == 0) continue;

            obj.onPrePhysicsTick(world);
        }
    }

    @Override
    public void onPhysicsTick(VxPhysicsWorld world, VxServerBodyDataStore store) {
        final long mask = VxBehaviors.TICK.getMask();
        final VxBody[] bodies = store.bodies;
        final int capacity = store.getCapacity();

        for (int i = 0; i < capacity; i++) {
            VxBody obj = bodies[i];
            if (obj == null) continue;
            if ((store.behaviorBits[i] & mask) == 0) continue;

            obj.onPhysicsTick(world);
        }
    }
}