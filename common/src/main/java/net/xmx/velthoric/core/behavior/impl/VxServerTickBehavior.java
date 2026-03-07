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

/**
 * The behavior for server-tick callbacks.
 * <p>
 * Bodies with this behavior receive per-game-tick updates on the main server thread
 * (typically 20 times per second).
 * <p>
 * This behavior is opt-in: only bodies that explicitly need server-tick logic
 * should have this behavior attached, reducing unnecessary iteration.
 *
 * @author xI-Mx-Ix
 */
public class VxServerTickBehavior implements VxBehavior {

    @Override
    public VxBehaviorId getId() {
        return VxBehaviors.SERVER_TICK;
    }

    @Override
    public void onServerTick(ServerLevel level, VxServerBodyDataStore store) {
        final long mask = VxBehaviors.SERVER_TICK.getMask();
        final VxBody[] bodies = store.bodies;
        final int capacity = store.getCapacity();

        for (int i = 0; i < capacity; i++) {
            VxBody obj = bodies[i];
            if (obj == null) continue;
            if ((store.behaviorBits[i] & mask) == 0) continue;

            obj.onServerTick(level);
        }
    }
}