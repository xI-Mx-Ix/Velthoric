package net.xmx.vortex.physics.object.physicsobject.client.interpolation;

import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import net.minecraft.util.Mth;
import net.xmx.vortex.math.VxOperations;
import net.xmx.vortex.math.VxTransform;
import net.xmx.vortex.physics.object.physicsobject.client.time.VxClientClock;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

public class InterpolationStateContainer {

    private static final long INTERPOLATION_DELAY_NANOS = 100_000_000L;
    private static final float MAX_EXTRAPOLATION_SECONDS = 0.05f;
    private static final int MAX_BUFFER_SIZE = 60;
    private static final int IDEAL_BUFFER_SIZE = 10;
    private static final long JITTER_THRESHOLD_NANOS = 50_000_000L;

    private final Deque<StateSnapshot> stateBuffer = new ArrayDeque<>();
    private final RenderData renderData = new RenderData();

    private long clockOffsetNanos = 0L;
    private boolean isClockOffsetInitialized = false;

    private final VxTransform lastGoodTransform = new VxTransform();
    @Nullable
    private float[] lastGoodVertices = null;

    public void addState(long serverTimestamp, VxTransform transform, @Nullable Vec3 linVel, @Nullable Vec3 angVel, @Nullable float[] vertices, boolean isActive) {
        if (!stateBuffer.isEmpty() && serverTimestamp <= stateBuffer.peekLast().serverTimestampNanos) {
            return;
        }

        long clientReceiptTime = VxClientClock.getInstance().getGameTimeNanos();
        if (!isClockOffsetInitialized) {
            this.clockOffsetNanos = serverTimestamp - clientReceiptTime;
            this.isClockOffsetInitialized = true;
        } else {
            long newOffset = serverTimestamp - clientReceiptTime;
            double factor = (Math.abs(newOffset - clockOffsetNanos) > JITTER_THRESHOLD_NANOS) ? 0.2 : 0.05;
            this.clockOffsetNanos = (long) (this.clockOffsetNanos * (1.0 - factor) + newOffset * factor);
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
        int targetSize = stateBuffer.size() > 2
                ? (stateBuffer.peekLast().serverTimestampNanos - stateBuffer.peekFirst().serverTimestampNanos > 60_000_000L * stateBuffer.size() ? MAX_BUFFER_SIZE : IDEAL_BUFFER_SIZE)
                : IDEAL_BUFFER_SIZE;

        while (stateBuffer.size() > targetSize) {
            StateSnapshot.release(stateBuffer.removeFirst());
        }
    }

    public RenderData getInterpolatedState(float partialTicks) {
        if (!isClockOffsetInitialized || stateBuffer.isEmpty()) {
            renderData.transform.set(lastGoodTransform);
            renderData.vertexData = lastGoodVertices;
            return renderData;
        }

        long estimatedServerTime = VxClientClock.getInstance().getGameTimeNanos() + this.clockOffsetNanos;
        long renderTimestamp = estimatedServerTime - INTERPOLATION_DELAY_NANOS;

        StateSnapshot from = null;
        StateSnapshot to = null;

        Iterator<StateSnapshot> it = stateBuffer.iterator();
        while (it.hasNext()) {
            StateSnapshot s = it.next();
            if (s.serverTimestampNanos <= renderTimestamp) {
                from = s;
            } else {
                to = s;
                break;
            }
        }

        if (from == null) {
            it = stateBuffer.iterator();
            if (it.hasNext()) from = it.next();
            if (it.hasNext()) to = it.next();
        }

        if (from == null) {
            renderData.transform.set(lastGoodTransform);
            renderData.vertexData = lastGoodVertices;
            return renderData;
        }

        if (to == null || to.serverTimestampNanos <= from.serverTimestampNanos) {
            return extrapolate(from, renderTimestamp);
        }

        long timeDiff = to.serverTimestampNanos - from.serverTimestampNanos;
        float alpha = Mth.clamp((float) (renderTimestamp - from.serverTimestampNanos) / timeDiff, 0.0f, 1.0f);

        return interpolate(from, to, alpha, timeDiff / 1_000_000_000.0f);
    }

    private RenderData interpolate(StateSnapshot from, StateSnapshot to, float alpha, float dt) {
        boolean useCubic = from.isActive && to.isActive &&
                from.linearVelocity != null && to.linearVelocity != null &&
                from.angularVelocity != null && to.angularVelocity != null;

        if (useCubic) {
            VxOperations.cubicHermite(from.transform.getTranslation(), from.linearVelocity, to.transform.getTranslation(), to.linearVelocity, alpha, dt, this.renderData.transform.getTranslation());
        } else {
            VxOperations.lerp(from.transform.getTranslation(), to.transform.getTranslation(), alpha, this.renderData.transform.getTranslation());
        }

        VxOperations.slerp(from.transform.getRotation(), to.transform.getRotation(), alpha, this.renderData.transform.getRotation());

        if (from.vertexData != null && to.vertexData != null && from.vertexData.length == to.vertexData.length) {
            if (this.renderData.vertexData == null || this.renderData.vertexData.length != from.vertexData.length) {
                this.renderData.vertexData = new float[from.vertexData.length];
            }
            for (int i = 0; i < from.vertexData.length; i++) {
                this.renderData.vertexData[i] = Mth.lerp(alpha, from.vertexData[i], to.vertexData[i]);
            }
        } else {
            this.renderData.vertexData = to.vertexData != null ? to.vertexData : from.vertexData;
        }

        return this.renderData;
    }

    private RenderData extrapolate(StateSnapshot from, long renderTimestamp) {
        if (!from.isActive || from.linearVelocity == null || from.angularVelocity == null) {
            this.renderData.transform.set(from.transform);
            this.renderData.vertexData = from.vertexData;
            return this.renderData;
        }

        float dt = (renderTimestamp - from.serverTimestampNanos) / 1_000_000_000.0f;
        if (dt > 0 && dt < MAX_EXTRAPOLATION_SECONDS) {
            VxOperations.extrapolatePosition(from.transform.getTranslation(), from.linearVelocity, dt, this.renderData.transform.getTranslation());
            VxOperations.extrapolateRotation(from.transform.getRotation(), from.angularVelocity, dt, this.renderData.transform.getRotation());
            this.renderData.vertexData = from.vertexData;
        } else {
            this.renderData.transform.set(from.transform);
            this.renderData.vertexData = from.vertexData;
        }
        return this.renderData;
    }

    @Nullable
    public RVec3 getLastKnownPosition() {
        if (stateBuffer.isEmpty()) {
            return lastGoodTransform.getTranslation();
        }
        return stateBuffer.peekLast().transform.getTranslation();
    }

    @Nullable
    public float[] getLatestVertexData() {
        if (stateBuffer.isEmpty()) {
            return lastGoodVertices;
        }
        StateSnapshot lastSnapshot = stateBuffer.peekLast();
        return lastSnapshot != null ? lastSnapshot.vertexData : null;
    }

    public void release() {
        stateBuffer.forEach(StateSnapshot::release);
        stateBuffer.clear();
        isClockOffsetInitialized = false;
        lastGoodVertices = null;
    }
}