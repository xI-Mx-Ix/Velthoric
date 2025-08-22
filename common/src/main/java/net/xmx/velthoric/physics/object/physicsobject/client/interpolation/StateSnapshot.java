package net.xmx.velthoric.physics.object.physicsobject.client.interpolation;

import com.github.stephengold.joltjni.Vec3;
import net.xmx.velthoric.math.VxTransform;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentLinkedQueue;

public class StateSnapshot {
    public long serverTimestampNanos;
    public final VxTransform transform = new VxTransform();
    @Nullable public final Vec3 linearVelocity;
    @Nullable public final Vec3 angularVelocity;
    @Nullable
    public float[] vertexData;
    public boolean isActive;

    private static final ConcurrentLinkedQueue<StateSnapshot> POOL = new ConcurrentLinkedQueue<>();

    private StateSnapshot() {
        this.linearVelocity = new Vec3();
        this.angularVelocity = new Vec3();
    }

    public static StateSnapshot acquire() {
        StateSnapshot s = POOL.poll();
        return (s != null) ? s : new StateSnapshot();
    }

    public static void release(StateSnapshot s) {
        if (s != null) {
            s.reset();
            POOL.offer(s);
        }
    }

    public StateSnapshot set(long timestamp, VxTransform transform, @Nullable Vec3 linVel, @Nullable Vec3 angVel, @Nullable float[] vertices, boolean isActive) {
        this.serverTimestampNanos = timestamp;
        this.transform.set(transform);
        this.isActive = isActive;

        if (isActive && linVel != null && angVel != null) {
            this.linearVelocity.set(linVel);
            this.angularVelocity.set(angVel);
        } else {
            this.linearVelocity.loadZero();
            this.angularVelocity.loadZero();
        }

        if (vertices != null) {
            if (this.vertexData == null || this.vertexData.length != vertices.length) {
                this.vertexData = new float[vertices.length];
            }
            System.arraycopy(vertices, 0, this.vertexData, 0, vertices.length);
        } else {
            this.vertexData = null;
        }
        return this;
    }

    private void reset() {
        this.serverTimestampNanos = 0;
        this.vertexData = null;
        this.isActive = false;
        this.transform.loadIdentity();
        this.linearVelocity.loadZero();
        this.angularVelocity.loadZero();
    }
}