package net.xmx.xbullet.physics.object.softphysicsobject.client;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.xmx.xbullet.math.PhysicsOperations;
import net.xmx.xbullet.math.PhysicsTransform;
import net.xmx.xbullet.physics.object.softphysicsobject.SoftPhysicsObject;
import net.xmx.xbullet.physics.world.time.ClientClock;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

public class ClientSoftPhysicsObjectData {
    private final UUID id;
    @Nullable private final SoftPhysicsObject.Renderer renderer;

    private final Deque<TimestampedState> vertexStateBuffer = new ArrayDeque<>();
    private final Deque<TimestampedTransform> transformStateBuffer = new ArrayDeque<>();

    private static final long INTERPOLATION_DELAY_MS = 100;
    private static final int MAX_BUFFER_SIZE = 250;
    private static final long MAX_BUFFER_TIME_MS = 2000;
    private static final int MIN_BUFFER_FOR_INTERPOLATION = 2;

    private long clockOffsetNanos = 0L;
    private boolean isClockOffsetInitialized = false;
    private static final double OFFSET_SMOOTHING_FACTOR = 0.05;

    @Nullable private float[] latestSyncedVertexData = null;
    @Nullable private PhysicsTransform latestSyncedTransform = null;
    private long lastServerTimestamp = 0;

    private float[] renderVertexBuffer = null;
    private final PhysicsTransform renderTransform = new PhysicsTransform();
    private CompoundTag syncedNbtData = new CompoundTag();

    public ClientSoftPhysicsObjectData(UUID id, @Nullable SoftPhysicsObject.Renderer renderer, long initialTimestamp) {
        this.id = id;
        this.renderer = renderer;
        this.lastServerTimestamp = initialTimestamp;
    }

    public void updateDataFromServer(@Nullable PhysicsTransform transform, @Nullable Vec3 linearVel, @Nullable Vec3 angularVel, @Nullable float[] newVertexData, long serverTimestamp, boolean isActive) {
        if (serverTimestamp <= 0) return;

        long clientReceiptTime = ClientClock.getInstance().getGameTimeNanos();
        if (!isClockOffsetInitialized) {
            clockOffsetNanos = serverTimestamp - clientReceiptTime;
            isClockOffsetInitialized = true;
        } else {
            long newOffset = serverTimestamp - clientReceiptTime;
            clockOffsetNanos = (long) (clockOffsetNanos * (1.0 - OFFSET_SMOOTHING_FACTOR) + newOffset * OFFSET_SMOOTHING_FACTOR);
        }

        if (serverTimestamp > this.lastServerTimestamp) {
            if (newVertexData != null && newVertexData.length > 0) {
                vertexStateBuffer.addLast(new TimestampedState(serverTimestamp, newVertexData, isActive));
                this.latestSyncedVertexData = newVertexData;
            }
            if (transform != null) {
                transformStateBuffer.addLast(new TimestampedTransform(serverTimestamp, transform.copy(), isActive));
                this.latestSyncedTransform = transform.copy();
            }
            this.lastServerTimestamp = serverTimestamp;
        }
    }

    public void updateNbt(CompoundTag nbt) {
        this.syncedNbtData = nbt.copy();
    }

    @Nullable
    public float[] getRenderVertexData(float partialTicks) {
        if (vertexStateBuffer.size() < MIN_BUFFER_FOR_INTERPOLATION || !isClockOffsetInitialized) {
            return latestSyncedVertexData;
        }

        long renderTimestamp = ClientClock.getInstance().getGameTimeNanos() + clockOffsetNanos - (INTERPOLATION_DELAY_MS * 1_000_000L);
        TimestampedState latest = vertexStateBuffer.peekLast();
        if (latest != null && !latest.isActive) {
            return latest.vertexData;
        }

        TimestampedState before = null, after = null;
        for (TimestampedState current : vertexStateBuffer) {
            if (current.timestampNanos <= renderTimestamp) {
                before = current;
            } else {
                after = current;
                break;
            }
        }

        if (before == null) {
            return vertexStateBuffer.isEmpty() ? latestSyncedVertexData : vertexStateBuffer.peekFirst().vertexData;
        }
        if (after == null) {
            return latestSyncedVertexData;
        }
        if (renderVertexBuffer == null || renderVertexBuffer.length != before.vertexData.length) {
            renderVertexBuffer = new float[before.vertexData.length];
        }

        long timeDiff = after.timestampNanos - before.timestampNanos;
        float alpha = (timeDiff <= 0) ? 1.0f : Mth.clamp((float)(renderTimestamp - before.timestampNanos) / timeDiff, 0.0f, 1.0f);

        for (int i = 0; i < before.vertexData.length; i++) {
            renderVertexBuffer[i] = Mth.lerp(alpha, before.vertexData[i], after.vertexData[i]);
        }
        return renderVertexBuffer;
    }

    @Nullable
    public PhysicsTransform getRenderTransform(float partialTicks) {
        if (transformStateBuffer.size() < MIN_BUFFER_FOR_INTERPOLATION || !isClockOffsetInitialized) {
            return latestSyncedTransform;
        }

        long renderTimestamp = ClientClock.getInstance().getGameTimeNanos() + clockOffsetNanos - (INTERPOLATION_DELAY_MS * 1_000_000L);
        TimestampedTransform latest = transformStateBuffer.peekLast();
        if (latest != null && !latest.isActive) {
            return latest.transform;
        }

        TimestampedTransform before = null, after = null;
        for (TimestampedTransform current : transformStateBuffer) {
            if (current.timestampNanos <= renderTimestamp) {
                before = current;
            } else {
                after = current;
                break;
            }
        }

        if (before == null) {
            return transformStateBuffer.isEmpty() ? latestSyncedTransform : transformStateBuffer.peekFirst().transform;
        }
        if (after == null) {
            return latestSyncedTransform;
        }

        long timeDiff = after.timestampNanos - before.timestampNanos;
        float alpha = (timeDiff <= 0) ? 1.0f : Mth.clamp((float)(renderTimestamp - before.timestampNanos) / timeDiff, 0.0f, 1.0f);

        PhysicsOperations.lerp(before.transform.getTranslation(), after.transform.getTranslation(), alpha, renderTransform.getTranslation());
        PhysicsOperations.slerp(before.transform.getRotation(), after.transform.getRotation(), alpha, renderTransform.getRotation());

        return renderTransform;
    }

    public void cleanupBuffer() {
        if (!isClockOffsetInitialized) return;
        long timeHorizonNanos = ClientClock.getInstance().getGameTimeNanos() + clockOffsetNanos - (MAX_BUFFER_TIME_MS * 1_000_000L);

        while (vertexStateBuffer.size() > MIN_BUFFER_FOR_INTERPOLATION && vertexStateBuffer.peekFirst().timestampNanos < timeHorizonNanos) {
            vertexStateBuffer.removeFirst();
        }
        while (vertexStateBuffer.size() > MAX_BUFFER_SIZE) {
            vertexStateBuffer.removeFirst();
        }

        while (transformStateBuffer.size() > MIN_BUFFER_FOR_INTERPOLATION && transformStateBuffer.peekFirst().timestampNanos < timeHorizonNanos) {
            transformStateBuffer.removeFirst();
        }
        while (transformStateBuffer.size() > MAX_BUFFER_SIZE) {
            transformStateBuffer.removeFirst();
        }
    }

    public UUID getId() { return id; }
    @Nullable public SoftPhysicsObject.Renderer getRenderer() { return renderer; }
    public CompoundTag getSyncedNbtData() { return syncedNbtData.copy(); }
    @Nullable public float[] getLatestVertexData() { return latestSyncedVertexData; }

    private record TimestampedState(long timestampNanos, float[] vertexData, boolean isActive) {}
    private record TimestampedTransform(long timestampNanos, PhysicsTransform transform, boolean isActive) {}
}