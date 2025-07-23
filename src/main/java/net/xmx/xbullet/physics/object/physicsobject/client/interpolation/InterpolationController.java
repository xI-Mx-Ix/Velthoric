package net.xmx.xbullet.physics.object.physicsobject.client.interpolation;

import com.github.stephengold.joltjni.Vec3;
import net.minecraft.util.Mth;
import net.xmx.xbullet.math.PhysicsOperations;
import net.xmx.xbullet.math.PhysicsTransform;
import net.xmx.xbullet.physics.object.physicsobject.client.time.ClientClock;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;

public class InterpolationController {

    private static final long INTERPOLATION_DELAY_NANOS = 100_000_000L;

    private static final float MAX_EXTRAPOLATION_SECONDS = 0.05f;

    private static final int MAX_BUFFER_SIZE = 60;
    private static final int IDEAL_BUFFER_SIZE = 10;

    private static final long JITTER_THRESHOLD_NANOS = 50_000_000L;

    private final Deque<StateSnapshot> stateBuffer = new ArrayDeque<>();
    private final StateSnapshot renderState = StateSnapshot.acquire();

    private long clockOffsetNanos = 0L;
    private boolean isClockOffsetInitialized = false;
    private long lastAddedTimestamp = 0L;

    private final PhysicsTransform lastGoodTransform = new PhysicsTransform();
    @Nullable private float[] lastGoodVertices = null;

    public void addState(long serverTimestamp, PhysicsTransform transform, @Nullable Vec3 linVel, @Nullable Vec3 angVel, @Nullable float[] vertices, boolean isActive) {

        if (!stateBuffer.isEmpty() && serverTimestamp <= stateBuffer.peekLast().serverTimestampNanos) {
            return;
        }

        long timeSinceLast = serverTimestamp - lastAddedTimestamp;
        if (lastAddedTimestamp > 0 && timeSinceLast > JITTER_THRESHOLD_NANOS * 2) {
            handleJitterEvent();
        }
        lastAddedTimestamp = serverTimestamp;

        long clientReceiptTime = ClientClock.getInstance().getGameTimeNanos();
        if (!isClockOffsetInitialized) {
            this.clockOffsetNanos = serverTimestamp - clientReceiptTime;
            this.isClockOffsetInitialized = true;
        } else {
            long newOffset = serverTimestamp - clientReceiptTime;

            double factor = (Math.abs(newOffset - clockOffsetNanos) > JITTER_THRESHOLD_NANOS) ?
                    0.2 : 0.05;
            this.clockOffsetNanos = (long) (this.clockOffsetNanos * (1.0 - factor) + newOffset * factor);
        }

        StateSnapshot snapshot = StateSnapshot.acquire().set(serverTimestamp, transform, linVel, angVel, vertices, isActive);
        stateBuffer.addLast(snapshot);

        lastGoodTransform.set(transform);
        if (vertices != null) {
            lastGoodVertices = vertices;
        }

        cleanupBuffer();
    }

    private void handleJitterEvent() {

        if (stateBuffer.size() > 2) {
            StateSnapshot lastValid = stateBuffer.peekLast();
            stateBuffer.clear();
            if (lastValid != null) {
                stateBuffer.add(lastValid);
            }
        }
        isClockOffsetInitialized = false;
    }

    private void cleanupBuffer() {

        int targetSize = IDEAL_BUFFER_SIZE;

        if (stateBuffer.size() > 2) {
            long avgInterval = (stateBuffer.peekLast().serverTimestampNanos -
                    stateBuffer.peekFirst().serverTimestampNanos) / stateBuffer.size();

            if (avgInterval > 60_000_000L) {
                targetSize = MAX_BUFFER_SIZE;
            }
        }

        while (stateBuffer.size() > targetSize) {
            StateSnapshot.release(stateBuffer.removeFirst());
        }
    }

    public StateSnapshot getInterpolatedState(float partialTicks) {
        if (!isClockOffsetInitialized || stateBuffer.isEmpty()) {
            renderState.transform.set(lastGoodTransform);
            renderState.vertexData = lastGoodVertices;
            return renderState;
        }

        long estimatedServerTime = ClientClock.getInstance().getGameTimeNanos() + this.clockOffsetNanos;
        long renderTimestamp = estimatedServerTime - INTERPOLATION_DELAY_NANOS;

        StateSnapshot from = null;
        StateSnapshot to = null;
        for (StateSnapshot s : stateBuffer) {
            if (s.serverTimestampNanos <= renderTimestamp) {
                from = s;
            } else {
                to = s;
                break;
            }
        }

        if (from == null) {
            from = stateBuffer.peekFirst();
            to = stateBuffer.size() > 1 ? stateBuffer.toArray(new StateSnapshot[0])[1] : null;
        }

        if (to == null) {
            return extrapolate(from, renderTimestamp);
        }

        if (to.serverTimestampNanos <= from.serverTimestampNanos) {
            return from;
        }

        long timeDiff = to.serverTimestampNanos - from.serverTimestampNanos;
        float alpha = (float) (renderTimestamp - from.serverTimestampNanos) / timeDiff;
        alpha = Mth.clamp(alpha, 0.0f, 1.0f);

        interpolate(from, to, alpha, timeDiff / 1_000_000_000.0f);
        return renderState;
    }

    private void interpolate(StateSnapshot from, StateSnapshot to, float alpha, float dt) {

        boolean useCubic = from.isActive && to.isActive &&
                from.linearVelocity != null && to.linearVelocity != null &&
                from.angularVelocity != null && to.angularVelocity != null;

        if (useCubic) {
            PhysicsOperations.cubicHermite(
                    from.transform.getTranslation(), from.linearVelocity,
                    to.transform.getTranslation(), to.linearVelocity,
                    alpha, dt, renderState.transform.getTranslation()
            );
        } else {
            PhysicsOperations.lerp(
                    from.transform.getTranslation(),
                    to.transform.getTranslation(),
                    alpha,
                    renderState.transform.getTranslation()
            );
        }

        PhysicsOperations.slerp(
                from.transform.getRotation(),
                to.transform.getRotation(),
                alpha,
                renderState.transform.getRotation()
        );

        if (from.vertexData != null && to.vertexData != null &&
                from.vertexData.length == to.vertexData.length) {

            if (renderState.vertexData == null ||
                    renderState.vertexData.length != from.vertexData.length) {
                renderState.vertexData = new float[from.vertexData.length];
            }

            for (int i = 0; i < from.vertexData.length; i++) {
                renderState.vertexData[i] = Mth.lerp(alpha, from.vertexData[i], to.vertexData[i]);
            }
        } else if (to.vertexData != null) {
            renderState.vertexData = to.vertexData;
        } else {
            renderState.vertexData = from.vertexData;
        }
    }

    private StateSnapshot extrapolate(StateSnapshot from, long renderTimestamp) {
        if (!from.isActive || from.linearVelocity == null || from.angularVelocity == null) {
            renderState.transform.set(from.transform);
            renderState.vertexData = from.vertexData;
            return renderState;
        }

        long timeSinceUpdate = renderTimestamp - from.serverTimestampNanos;
        float dt = timeSinceUpdate / 1_000_000_000.0f;

        if (dt > 0 && dt < MAX_EXTRAPOLATION_SECONDS) {
            PhysicsOperations.extrapolatePosition(
                    from.transform.getTranslation(),
                    from.linearVelocity,
                    dt,
                    renderState.transform.getTranslation()
            );
            PhysicsOperations.extrapolateRotation(
                    from.transform.getRotation(),
                    from.angularVelocity,
                    dt,
                    renderState.transform.getRotation()
            );
            renderState.vertexData = from.vertexData;
        } else {
            renderState.transform.set(from.transform);
            renderState.vertexData = from.vertexData;
        }
        return renderState;
    }

    public void release() {
        stateBuffer.forEach(StateSnapshot::release);
        stateBuffer.clear();
    }
}