package net.xmx.velthoric.physics.object.physicsobject.client.interpolation;

import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import net.minecraft.util.Mth;
import net.xmx.velthoric.math.VxOperations;
import net.xmx.velthoric.math.VxTransform;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

public class InterpolationStateContainer {

    private static final float MAX_EXTRAPOLATION_SECONDS = 0.1f;
    private static final int MAX_BUFFER_SIZE = 60;
    private static final long BUFFER_TIME_NANOS = 200_000_000L;
    private static final float CUBIC_INTERPOLATION_TIME_THRESHOLD_SECONDS = 0.1f;
    private static final long BRIDGE_STATE_TIME_OFFSET_NANOS = 1L;

    private final Deque<StateSnapshot> stateBuffer = new ArrayDeque<>();
    private final VxTransform lastGoodTransform = new VxTransform();
    @Nullable
    private float[] lastGoodVertices = null;

    public void addState(long serverTimestamp, VxTransform transform, @Nullable Vec3 linVel, @Nullable Vec3 angVel, @Nullable float[] vertices, boolean isActive) {
        if (!stateBuffer.isEmpty() && serverTimestamp <= stateBuffer.peekLast().serverTimestampNanos) {
            return;
        }

        StateSnapshot last = stateBuffer.peekLast();
        if (last != null && !last.isActive && isActive) {
            StateSnapshot bridgeState = StateSnapshot.acquire();
            bridgeState.transform.set(last.transform);
            bridgeState.serverTimestampNanos = serverTimestamp - BRIDGE_STATE_TIME_OFFSET_NANOS;
            bridgeState.isActive = true;
            bridgeState.linearVelocity.loadZero();
            bridgeState.angularVelocity.loadZero();
            bridgeState.vertexData = last.vertexData;
            stateBuffer.addLast(bridgeState);
        }

        if (vertices != null) {
            this.lastGoodVertices = vertices;
        }
        float[] verticesForSnapshot = (vertices != null) ? vertices : this.lastGoodVertices;

        StateSnapshot snapshot = StateSnapshot.acquire().set(serverTimestamp, transform, linVel, angVel, verticesForSnapshot, isActive);
        stateBuffer.addLast(snapshot);

        lastGoodTransform.set(transform);
        cleanupBuffer();
    }

    private void cleanupBuffer() {
        if (stateBuffer.size() < 2) return;

        long lastTimestamp = stateBuffer.peekLast().serverTimestampNanos;
        long cutoffTimestamp = lastTimestamp - BUFFER_TIME_NANOS;

        while (stateBuffer.size() > 2 && stateBuffer.peekFirst().serverTimestampNanos < cutoffTimestamp) {
            StateSnapshot.release(stateBuffer.removeFirst());
        }

        while (stateBuffer.size() > MAX_BUFFER_SIZE) {
            StateSnapshot.release(stateBuffer.removeFirst());
        }
    }

    public void getInterpolatedState(long renderTimestamp, RenderState out) {
        if (stateBuffer.size() < 2) {
            if (!stateBuffer.isEmpty()) {
                extrapolate(stateBuffer.peekFirst(), renderTimestamp, out);
            } else {
                out.transform.set(lastGoodTransform);
                out.vertexData = lastGoodVertices;
            }
            return;
        }

        StateSnapshot from = null;
        StateSnapshot to = null;

        Iterator<StateSnapshot> it = stateBuffer.descendingIterator();
        while (it.hasNext()) {
            StateSnapshot current = it.next();
            if (current.serverTimestampNanos <= renderTimestamp) {
                from = current;
                break;
            }
            to = current;
        }

        if (from != null && to == null) {
            Iterator<StateSnapshot> forwardIt = stateBuffer.iterator();
            while (forwardIt.hasNext()) {
                StateSnapshot s = forwardIt.next();
                if (s == from && forwardIt.hasNext()) {
                    to = forwardIt.next();
                    break;
                }
            }
        }

        if (from == null) {
            StateSnapshot oldest = stateBuffer.peekFirst();
            out.transform.set(oldest.transform);
            out.vertexData = oldest.vertexData;
            return;
        }

        if (to == null) {
            extrapolate(from, renderTimestamp, out);
            return;
        }

        long timeDiff = to.serverTimestampNanos - from.serverTimestampNanos;
        if (timeDiff <= 0) {
            out.transform.set(from.transform);
            out.vertexData = from.vertexData;
            return;
        }

        float alpha = Mth.clamp((float) (renderTimestamp - from.serverTimestampNanos) / timeDiff, 0.0f, 1.0f);
        interpolate(from, to, alpha, timeDiff / 1_000_000_000.0f, out);
    }

    private void interpolate(StateSnapshot from, StateSnapshot to, float alpha, float dt, RenderState out) {
        boolean useCubic = from.isActive && to.isActive &&
                from.linearVelocity != null && to.linearVelocity != null &&
                from.angularVelocity != null && to.angularVelocity != null &&
                dt < CUBIC_INTERPOLATION_TIME_THRESHOLD_SECONDS;

        if (useCubic) {
            VxOperations.cubicHermite(from.transform.getTranslation(), from.linearVelocity, to.transform.getTranslation(), to.linearVelocity, alpha, dt, out.transform.getTranslation());
        } else {
            VxOperations.lerp(from.transform.getTranslation(), to.transform.getTranslation(), alpha, out.transform.getTranslation());
        }

        VxOperations.slerp(from.transform.getRotation(), to.transform.getRotation(), alpha, out.transform.getRotation());

        if (from.vertexData != null && to.vertexData != null && from.vertexData.length == to.vertexData.length) {
            if (out.vertexData == null || out.vertexData.length != from.vertexData.length) {
                out.vertexData = new float[from.vertexData.length];
            }
            for (int i = 0; i < from.vertexData.length; i++) {
                out.vertexData[i] = Mth.lerp(alpha, from.vertexData[i], to.vertexData[i]);
            }
        } else {
            out.vertexData = to.vertexData != null ? to.vertexData : from.vertexData;
        }
    }

    private void extrapolate(StateSnapshot from, long renderTimestamp, RenderState out) {
        if (!from.isActive || from.linearVelocity == null || from.angularVelocity == null) {
            out.transform.set(from.transform);
            out.vertexData = from.vertexData;
            return;
        }

        float dt = (float) (renderTimestamp - from.serverTimestampNanos) / 1_000_000_000.0f;

        if (dt > 0 && dt < MAX_EXTRAPOLATION_SECONDS) {
            VxOperations.extrapolatePosition(from.transform.getTranslation(), from.linearVelocity, dt, out.transform.getTranslation());
            VxOperations.extrapolateRotation(from.transform.getRotation(), from.angularVelocity, dt, out.transform.getRotation());
            out.vertexData = from.vertexData;
        } else {
            out.transform.set(from.transform);
            out.vertexData = from.vertexData;
        }
    }

    @Nullable
    public RVec3 getLastKnownPosition() {
        if (stateBuffer.isEmpty()) {
            return lastGoodTransform.getTranslation();
        }
        return stateBuffer.peekLast().transform.getTranslation();
    }

    public float @Nullable [] getLatestVertexData() {
        if (stateBuffer.isEmpty()) {
            return lastGoodVertices;
        }
        StateSnapshot lastSnapshot = stateBuffer.peekLast();
        return lastSnapshot != null ? lastSnapshot.vertexData : null;
    }

    public void release() {
        stateBuffer.forEach(StateSnapshot::release);
        stateBuffer.clear();
        lastGoodVertices = null;
    }
}