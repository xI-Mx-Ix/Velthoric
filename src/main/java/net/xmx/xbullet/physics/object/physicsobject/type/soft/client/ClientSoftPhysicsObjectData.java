package net.xmx.xbullet.physics.object.physicsobject.type.soft.client;

import com.github.stephengold.joltjni.Vec3;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.xmx.xbullet.math.PhysicsOperations;
import net.xmx.xbullet.math.PhysicsTransform;
import net.xmx.xbullet.physics.object.physicsobject.type.soft.SoftPhysicsObject;
import net.xmx.xbullet.physics.object.physicsobject.type.soft.properties.SoftPhysicsObjectProperties;
import net.xmx.xbullet.physics.world.time.ClientClock;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ClientSoftPhysicsObjectData {
    private final UUID id;
    @Nullable
    private final SoftPhysicsObject.Renderer renderer;
    private final Deque<TimestampedState> vertexStateBuffer = new ArrayDeque<>();
    private final Deque<TimestampedTransform> transformStateBuffer = new ArrayDeque<>();
    private long lastServerTimestamp = 0;
    private byte[] customData;
    @Nullable private float[] latestSyncedVertexData = null;
    @Nullable private PhysicsTransform latestSyncedTransform = null;

    private static final long INTERPOLATION_DELAY_MS = 150;
    private static final int MAX_BUFFER_SIZE = 20;
    private static final long MAX_BUFFER_TIME_MS = 2000;
    private static final int MIN_BUFFER_FOR_INTERPOLATION = 2;
    private long clockOffsetNanos = 0L;
    private boolean isClockOffsetInitialized = false;
    private static final double OFFSET_SMOOTHING_FACTOR = 0.05;
    private static final float MAX_EXTRAPOLATION_SECONDS = 0.2f;

    private float[] renderVertexBuffer = null;
    private final PhysicsTransform renderTransform = new PhysicsTransform();

    public ClientSoftPhysicsObjectData(UUID id, @Nullable SoftPhysicsObject.Renderer renderer, long initialTimestamp) {
        this.id = id;
        this.renderer = renderer;
        this.lastServerTimestamp = initialTimestamp;
    }

    public void readData(ByteBuf buffer) {
        FriendlyByteBuf buf = new FriendlyByteBuf(buffer);
        latestSyncedTransform = new PhysicsTransform();
        latestSyncedTransform.fromBuffer(buf);
        SoftPhysicsObjectProperties.fromBuffer(buf);
        if (buf.readBoolean()) {
            int length = buf.readVarInt();
            this.latestSyncedVertexData = new float[length];
            for (int i = 0; i < length; i++) {
                this.latestSyncedVertexData[i] = buf.readFloat();
            }
        } else {
            this.latestSyncedVertexData = null;
        }
        if (buf.readableBytes() > 0) {
            this.customData = new byte[buf.readableBytes()];
            buf.readBytes(this.customData);
        } else {
            this.customData = new byte[0];
        }
        buf.skipBytes(buf.readableBytes());
        if (latestSyncedTransform != null) {
            transformStateBuffer.addLast(TimestampedTransformPool.acquire().set(lastServerTimestamp, latestSyncedTransform.copy(), null, null, true));
        }
        if (latestSyncedVertexData != null) {
            vertexStateBuffer.addLast(TimestampedStatePool.acquire().set(lastServerTimestamp, latestSyncedVertexData, true));
        }
    }

    public byte[] getCustomData() {
        return customData;
    }

    @Nullable
    public float[] getLatestVertexData() {
        return latestSyncedVertexData;
    }

    public void updateDataFromServer(@Nullable PhysicsTransform transform, @Nullable Vec3 linearVel, @Nullable Vec3 angularVel, @Nullable float[] newVertexData, long serverTimestamp, boolean isActive) {
        if (serverTimestamp <= 0) {
            return;
        }

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
                vertexStateBuffer.addLast(TimestampedStatePool.acquire().set(serverTimestamp, newVertexData, isActive));
                this.latestSyncedVertexData = newVertexData;
            }
            if (transform != null) {
                transformStateBuffer.addLast(TimestampedTransformPool.acquire().set(serverTimestamp, transform, linearVel, angularVel, isActive));
                this.latestSyncedTransform = transform.copy();
            }
            this.lastServerTimestamp = serverTimestamp;
        }
    }

    @Nullable
    public float[] getRenderVertexData(float partialTicks) {
        if (vertexStateBuffer.isEmpty()) {
            return latestSyncedVertexData;
        }
        TimestampedState latest = vertexStateBuffer.peekLast();
        if (latest == null) {
            return latestSyncedVertexData;
        }

        if (vertexStateBuffer.size() < MIN_BUFFER_FOR_INTERPOLATION || !isClockOffsetInitialized) {
            return latest.vertexData;
        }

        long renderTimestamp = ClientClock.getInstance().getGameTimeNanos() + clockOffsetNanos - (INTERPOLATION_DELAY_MS * 1_000_000L);
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
            // Für Vertex-Daten extrapolieren wir nicht, wir halten die letzte Position.
            // Die Hauptbewegung kommt von der Extrapolation der Gesamt-Transformation.
            return before.vertexData;
        }

        if (renderVertexBuffer == null || renderVertexBuffer.length != before.vertexData.length) {
            renderVertexBuffer = new float[before.vertexData.length];
        }

        long timeDiff = after.timestampNanos - before.timestampNanos;
        float alpha = (timeDiff <= 0) ? 1.0f : Mth.clamp((float) (renderTimestamp - before.timestampNanos) / timeDiff, 0.0f, 1.0f);

        for (int i = 0; i < before.vertexData.length; i++) {
            renderVertexBuffer[i] = Mth.lerp(alpha, before.vertexData[i], after.vertexData[i]);
        }
        return renderVertexBuffer;
    }

    @Nullable
    public PhysicsTransform getRenderTransform(float partialTicks) {
        if (transformStateBuffer.isEmpty()) {
            return latestSyncedTransform;
        }
        TimestampedTransform latest = transformStateBuffer.peekLast();
        if (latest == null) {
            return latestSyncedTransform;
        }

        if (transformStateBuffer.size() < MIN_BUFFER_FOR_INTERPOLATION || !isClockOffsetInitialized) {
            return latest.transform;
        }

        long renderTimestamp = ClientClock.getInstance().getGameTimeNanos() + clockOffsetNanos - (INTERPOLATION_DELAY_MS * 1_000_000L);
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
            // Extrapolation
            if (before.isActive) {
                long timeSinceLastUpdateNanos = renderTimestamp - before.timestampNanos;
                float dt = timeSinceLastUpdateNanos / 1_000_000_000.0f;

                if (dt > 0 && dt < MAX_EXTRAPOLATION_SECONDS) {
                    PhysicsOperations.extrapolatePosition(before.transform.getTranslation(), before.linearVelocity, dt, renderTransform.getTranslation());
                    PhysicsOperations.extrapolateRotation(before.transform.getRotation(), before.angularVelocity, dt, renderTransform.getRotation());
                    return renderTransform;
                }
            }
            return before.transform;
        }

        // Interpolation
        long timeDiffNanos = after.timestampNanos - before.timestampNanos;
        if (timeDiffNanos <= 0) {
            return after.transform;
        }

        float dtSeconds = timeDiffNanos / 1_000_000_000f;
        float alpha = Mth.clamp((float) (renderTimestamp - before.timestampNanos) / timeDiffNanos, 0.0f, 1.0f);

        PhysicsOperations.cubicHermite(
                before.transform.getTranslation(), before.linearVelocity,
                after.transform.getTranslation(), after.linearVelocity,
                alpha, dtSeconds, renderTransform.getTranslation()
        );
        PhysicsOperations.interpolateCubic(
                before.transform.getRotation(), before.angularVelocity,
                after.transform.getRotation(), after.angularVelocity,
                dtSeconds, alpha, renderTransform.getRotation()
        );

        return renderTransform;
    }

    public void cleanupBuffer() {
        if (!isClockOffsetInitialized) {
            return;
        }

        long timeHorizonNanos = ClientClock.getInstance().getGameTimeNanos() + clockOffsetNanos - (MAX_BUFFER_TIME_MS * 1_000_000L);

        while (vertexStateBuffer.size() > MIN_BUFFER_FOR_INTERPOLATION && vertexStateBuffer.peekFirst().timestampNanos < timeHorizonNanos) {
            TimestampedStatePool.release(vertexStateBuffer.removeFirst());
        }
        while (vertexStateBuffer.size() > MAX_BUFFER_SIZE) {
            TimestampedStatePool.release(vertexStateBuffer.removeFirst());
        }

        while (transformStateBuffer.size() > MIN_BUFFER_FOR_INTERPOLATION && transformStateBuffer.peekFirst().timestampNanos < timeHorizonNanos) {
            TimestampedTransformPool.release(transformStateBuffer.removeFirst());
        }
        while (transformStateBuffer.size() > MAX_BUFFER_SIZE) {
            TimestampedTransformPool.release(transformStateBuffer.removeFirst());
        }
    }

    public void releaseAll() {
        vertexStateBuffer.forEach(TimestampedStatePool::release);
        vertexStateBuffer.clear();
        transformStateBuffer.forEach(TimestampedTransformPool::release);
        transformStateBuffer.clear();
    }

    public UUID getId() { return id; }

    @Nullable
    public SoftPhysicsObject.Renderer getRenderer() { return renderer; }

    private static class TimestampedState {
        long timestampNanos;
        float[] vertexData;
        boolean isActive;

        public TimestampedState set(long timestamp, float[] vertexData, boolean isActive) {
            this.timestampNanos = timestamp;
            if (this.vertexData == null || this.vertexData.length != vertexData.length) {
                this.vertexData = new float[vertexData.length];
            }
            System.arraycopy(vertexData, 0, this.vertexData, 0, vertexData.length);
            this.isActive = isActive;
            return this;
        }

        public void reset() {
            this.timestampNanos = 0;
            this.vertexData = null;
            this.isActive = false;
        }
    }

    private static class TimestampedStatePool {
        private static final ConcurrentLinkedQueue<TimestampedState> POOL = new ConcurrentLinkedQueue<>();
        static TimestampedState acquire() {
            TimestampedState obj = POOL.poll();
            return (obj != null) ? obj : new TimestampedState();
        }
        static void release(TimestampedState obj) {
            if (obj != null) {
                obj.reset();
                POOL.offer(obj);
            }
        }
    }

    private static class TimestampedTransform {
        long timestampNanos;
        final PhysicsTransform transform = new PhysicsTransform();
        final Vec3 linearVelocity = new Vec3();
        final Vec3 angularVelocity = new Vec3();
        boolean isActive;

        public TimestampedTransform set(long timestamp, PhysicsTransform source, @Nullable Vec3 linVel, @Nullable Vec3 angVel, boolean isActive) {
            this.timestampNanos = timestamp;
            this.transform.set(source);
            if (linVel != null) { this.linearVelocity.set(linVel); } else { this.linearVelocity.loadZero(); }
            if (angVel != null) { this.angularVelocity.set(angVel); } else { this.angularVelocity.loadZero(); }
            this.isActive = isActive;
            return this;
        }
        public void reset() {
            this.timestampNanos = 0;
            this.isActive = false;
            this.transform.loadIdentity();
            this.linearVelocity.loadZero();
            this.angularVelocity.loadZero();
        }
    }

    private static class TimestampedTransformPool {
        private static final ConcurrentLinkedQueue<TimestampedTransform> POOL = new ConcurrentLinkedQueue<>();
        static TimestampedTransform acquire() {
            TimestampedTransform obj = POOL.poll();
            return (obj != null) ? obj : new TimestampedTransform();
        }
        static void release(TimestampedTransform obj) {
            if (obj != null) {
                obj.reset();
                POOL.offer(obj);
            }
        }
    }
}