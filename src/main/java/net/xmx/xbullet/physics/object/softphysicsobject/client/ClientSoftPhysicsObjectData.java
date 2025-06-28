package net.xmx.xbullet.physics.object.softphysicsobject.client;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.xmx.xbullet.physics.object.softphysicsobject.SoftPhysicsObject;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

public class ClientSoftPhysicsObjectData {
    private final UUID id;
    @Nullable private final SoftPhysicsObject.Renderer renderer;
    private final Deque<TimestampedState> stateBuffer = new ArrayDeque<>();

    private static final long INTERPOLATION_DELAY_MS = 100;
    private static final int MAX_BUFFER_SIZE = 250;
    private static final long MAX_BUFFER_TIME_MS = 2000;
    private static final int MIN_BUFFER_FOR_INTERPOLATION = 2;

    private long clockOffsetNanos = 0L;
    private boolean isClockOffsetInitialized = false;
    private static final double OFFSET_SMOOTHING_FACTOR = 0.05;

    @Nullable private float[] latestSyncedVertexData = null;
    private long lastServerTimestamp = 0;
    private float[] renderBuffer = null;
    private CompoundTag syncedNbtData = new CompoundTag();
    private float mass;

    public ClientSoftPhysicsObjectData(UUID id, @Nullable SoftPhysicsObject.Renderer renderer, long initialTimestamp) {
        this.id = id;
        this.renderer = renderer;
        this.lastServerTimestamp = initialTimestamp;
    }

    public void updateVertexDataFromServer(@Nullable float[] newVertexData, long serverTimestamp, boolean isActive) {
        if (newVertexData == null || newVertexData.length == 0 || serverTimestamp <= 0) return;

        long clientReceiptTime = System.nanoTime();
        if (!isClockOffsetInitialized) {
            clockOffsetNanos = serverTimestamp - clientReceiptTime;
            isClockOffsetInitialized = true;
        } else {
            long newOffset = serverTimestamp - clientReceiptTime;
            clockOffsetNanos = (long) (clockOffsetNanos * (1.0 - OFFSET_SMOOTHING_FACTOR) + newOffset * OFFSET_SMOOTHING_FACTOR);
        }

        if (serverTimestamp > this.lastServerTimestamp) {
            stateBuffer.addLast(new TimestampedState(serverTimestamp, newVertexData, isActive));
            this.latestSyncedVertexData = newVertexData;
            this.lastServerTimestamp = serverTimestamp;
        }
    }

    public void updateNbt(CompoundTag nbt) {
        this.syncedNbtData = nbt.copy();
        if (this.syncedNbtData.contains("mass")) {
            this.mass = this.syncedNbtData.getFloat("mass");
        }
    }

    @Nullable
    public float[] getRenderVertexData(float partialTicks) {
        if (stateBuffer.size() < MIN_BUFFER_FOR_INTERPOLATION || !isClockOffsetInitialized) {
            return latestSyncedVertexData;
        }

        long renderTimestamp = System.nanoTime() + clockOffsetNanos - (INTERPOLATION_DELAY_MS * 1_000_000L);

        TimestampedState latest = stateBuffer.peekLast();
        if (latest != null && !latest.isActive) return latest.vertexData;

        TimestampedState before = null;
        TimestampedState after = null;
        for (TimestampedState current : stateBuffer) {
            if (current.timestampNanos <= renderTimestamp) before = current;
            else { after = current; break; }
        }

        if (before == null) {
            return stateBuffer.isEmpty() ? latestSyncedVertexData : stateBuffer.peekFirst().vertexData;
        }
        if (after == null) {
            return latestSyncedVertexData;
        }

        if (renderBuffer == null || renderBuffer.length != before.vertexData.length) {
            renderBuffer = new float[before.vertexData.length];
        }

        long timeDiff = after.timestampNanos - before.timestampNanos;
        float alpha = (timeDiff <= 0) ? 1.0f : (float)(renderTimestamp - before.timestampNanos) / timeDiff;
        alpha = Mth.clamp(alpha, 0.0f, 1.0f);

        for (int i = 0; i < before.vertexData.length; i++) {
            renderBuffer[i] = Mth.lerp(alpha, before.vertexData[i], after.vertexData[i]);
        }
        return renderBuffer;
    }

    public CompoundTag getSyncedNbtData() {
        return syncedNbtData.copy();
    }

    public void cleanupBuffer() {
        if (stateBuffer.isEmpty() || !isClockOffsetInitialized) return;

        long timeHorizonNanos = System.nanoTime() + clockOffsetNanos - (MAX_BUFFER_TIME_MS * 1_000_000L);
        while (stateBuffer.size() > MIN_BUFFER_FOR_INTERPOLATION && stateBuffer.peekFirst().timestampNanos < timeHorizonNanos) {
            stateBuffer.removeFirst();
        }
        while (stateBuffer.size() > MAX_BUFFER_SIZE) {
            stateBuffer.removeFirst();
        }
    }

    public UUID getId() { return id; }
    @Nullable public SoftPhysicsObject.Renderer getRenderer() { return renderer; }

    private static class TimestampedState {
        final long timestampNanos;
        final float[] vertexData;
        final boolean isActive;

        TimestampedState(long timestampNanos, float[] vertexData, boolean isActive) {
            this.timestampNanos = timestampNanos;
            this.vertexData = vertexData;
            this.isActive = isActive;
        }
    }

    @Nullable
    public float[] getLatestVertexData() {
        return latestSyncedVertexData;
    }
}