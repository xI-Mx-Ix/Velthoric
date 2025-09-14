/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.state;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * An object pool for {@link PhysicsObjectState} instances.
 * This helps to reduce garbage collection pressure by recycling state objects instead of
 * creating new ones for every incoming synchronization packet.
 *
 * @author xI-Mx-Ix
 */
public final class PhysicsObjectStatePool {

    // A thread-safe queue to hold the available (pooled) state objects.
    private static final ConcurrentLinkedQueue<PhysicsObjectState> POOL = new ConcurrentLinkedQueue<>();

    /**
     * Acquires a {@link PhysicsObjectState} from the pool. If the pool is empty,
     * a new instance is created.
     *
     * @return A ready-to-use {@link PhysicsObjectState} instance.
     */
    public static PhysicsObjectState acquire() {
        PhysicsObjectState state = POOL.poll();
        if (state == null) {
            state = new PhysicsObjectState();
        }
        return state;
    }

    /**
     * Releases a {@link PhysicsObjectState} back into the pool for future reuse.
     * The state object is reset before being added back to the pool.
     *
     * @param state The state object to release. Must not be null.
     */
    public static void release(PhysicsObjectState state) {
        if (state != null) {
            state.reset();
            POOL.offer(state);
        }
    }
}