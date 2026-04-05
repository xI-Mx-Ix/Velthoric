/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.behavior.impl;

import net.minecraft.server.level.ServerLevel;
import net.xmx.velthoric.core.behavior.VxBehavior;
import net.xmx.velthoric.core.behavior.VxBehaviorId;
import net.xmx.velthoric.core.body.VxBody;
import net.xmx.velthoric.core.body.server.VxServerBodyDataStore;
import net.xmx.velthoric.core.body.server.VxServerBodyDataContainer;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;
import net.xmx.velthoric.init.VxMainClass;

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

    /**
     * Default constructor for the ticking behavior.
     */
    public VxTickBehavior() {
    }

    /**
     * The unique identifier for this behavior.
     * Consumed by the behavior manager for bitmask allocation and dispatch.
     */
    public static final VxBehaviorId ID = new VxBehaviorId(VxMainClass.MODID, "Tick");

    /**
     * Retrieves the unique identifier for this behavior.
     *
     * @return The behavior ID.
     */
    @Override
    public VxBehaviorId getId() {
        return ID;
    }

    /**
     * Ticks the entities on the server thread.
     *
     * @param level The server level.
     * @param store The data store.
     */
    @Override
    public void onServerTick(ServerLevel level, VxServerBodyDataStore store) {
        VxServerBodyDataContainer c = store.serverCurrent();
        final long mask = ID.getMask();
        final VxBody[] bodies = c.bodies;
        final int capacity = c.getCapacity();

        for (int i = 0; i < capacity; i++) {
            VxBody obj = bodies[i];
            if (obj == null) continue;
            if ((c.behaviorBits[i] & mask) == 0) continue;

            obj.onServerTick(level);
        }
    }

    /**
     * Called before the Jolt simulation step.
     *
     * @param world The physics world.
     * @param store The data store.
     */
    @Override
    public void onPrePhysicsTick(VxPhysicsWorld world, VxServerBodyDataStore store) {
        VxServerBodyDataContainer c = store.serverCurrent();
        final long mask = ID.getMask();
        final VxBody[] bodies = c.bodies;
        final int capacity = c.getCapacity();

        for (int i = 0; i < capacity; i++) {
            VxBody obj = bodies[i];
            if (obj == null) continue;
            if ((c.behaviorBits[i] & mask) == 0) continue;
            if (!c.isActive[i]) continue;

            obj.onPrePhysicsTick(world);
        }
    }

    /**
     * Called after the Jolt simulation step.
     *
     * @param world The physics world.
     * @param store The data store.
     */
    @Override
    public void onPhysicsTick(VxPhysicsWorld world, VxServerBodyDataStore store) {
        VxServerBodyDataContainer c = store.serverCurrent();
        final long mask = ID.getMask();
        final VxBody[] bodies = c.bodies;
        final int capacity = c.getCapacity();

        for (int i = 0; i < capacity; i++) {
            VxBody obj = bodies[i];
            if (obj == null) continue;
            if ((c.behaviorBits[i] & mask) == 0) continue;
            if (!c.isActive[i]) continue;

            obj.onPhysicsTick(world);
        }
    }
}