package net.xmx.xbullet.physics.object.physicsobject.state;

import java.util.concurrent.ConcurrentLinkedQueue;

public final class PhysicsObjectStatePool {

    private static final ConcurrentLinkedQueue<PhysicsObjectState> POOL = new ConcurrentLinkedQueue<>();

    public static PhysicsObjectState acquire() {
        PhysicsObjectState state = POOL.poll();
        if (state == null) {
            state = new PhysicsObjectState();
        }
        return state;
    }

    public static void release(PhysicsObjectState state) {
        if (state != null) {
            state.reset(); 
            POOL.offer(state);
        }
    }
}