package net.xmx.xbullet.physics.object.physicsobject.type.rigid.client;

import com.github.stephengold.joltjni.Vec3;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.xmx.xbullet.math.PhysicsOperations;
import net.xmx.xbullet.math.PhysicsTransform;
import net.xmx.xbullet.physics.object.physicsobject.type.rigid.RigidPhysicsObject;
import net.xmx.xbullet.physics.world.time.ClientClock;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ClientRigidPhysicsObjectData {
    private final UUID id;
    @Nullable
    private final RigidPhysicsObject.Renderer renderer;
    private final Deque<TimestampedTransform> transformBuffer = new ArrayDeque<>();
    private final PhysicsTransform lastValidSnapshot = new PhysicsTransform();
    private final PhysicsTransform renderTransform = new PhysicsTransform();
    private long lastServerTimestamp = 0;
    private long clockOffsetNanos = 0L;
    private boolean isClockOffsetInitialized = false;

    private byte[] customData;

    private static final long INTERPOLATION_DELAY_MS = 150;
    private static final int MAX_BUFFER_SIZE = 20;
    private static final long MAX_BUFFER_TIME_MS = 2000;
    private static final int MIN_BUFFER_FOR_INTERPOLATION = 2;
    private static final double OFFSET_SMOOTHING_FACTOR = 0.05;

    public ClientRigidPhysicsObjectData(UUID id, @Nullable RigidPhysicsObject.Renderer renderer, long initialServerTimestampNanos) {
        this.id = id;
        this.renderer = renderer;
        this.lastValidSnapshot.loadIdentity();
        this.lastServerTimestamp = initialServerTimestampNanos;
    }

    public void readData(ByteBuf buffer) {
        FriendlyByteBuf buf = new FriendlyByteBuf(buffer);
        this.lastValidSnapshot.fromBuffer(buf);
        buf.readFloat(); buf.readFloat(); buf.readFloat();
        buf.readFloat(); buf.readFloat(); buf.readFloat(); buf.readFloat();
        buf.readUtf();
        buf.readFloat(); buf.readFloat(); buf.readFloat();
        buf.readFloat(); buf.readFloat(); buf.readFloat();

        if (buf.readableBytes() > 0) {
            this.customData = new byte[buf.readableBytes()];
            buf.readBytes(this.customData);
        } else {
            this.customData = new byte[0];
        }
        buf.skipBytes(buf.readableBytes());
        transformBuffer.addLast(TimestampedTransformPool.acquire().set(lastServerTimestamp, lastValidSnapshot.copy(), true));
    }

    public byte[] getCustomData() {
        return customData;
    }

    public void updateTransformFromServer(@Nullable PhysicsTransform newTransform, @Nullable Vec3 linVel, @Nullable Vec3 angVel, long serverTimestamp, boolean isActive) {
        PhysicsTransform transformToBuffer = newTransform != null ? newTransform : lastValidSnapshot;
        if (transformToBuffer == null || serverTimestamp <= 0) {
            return;
        }

        long clientReceiptTimeNanos = ClientClock.getInstance().getGameTimeNanos();
        if (!isClockOffsetInitialized) {
            this.clockOffsetNanos = serverTimestamp - clientReceiptTimeNanos;
            this.isClockOffsetInitialized = true;
        } else {
            long newOffset = serverTimestamp - clientReceiptTimeNanos;

            this.clockOffsetNanos = (long) (this.clockOffsetNanos * (1.0 - OFFSET_SMOOTHING_FACTOR) + newOffset * OFFSET_SMOOTHING_FACTOR);
        }

        if (serverTimestamp > lastServerTimestamp) {
            transformBuffer.addLast(TimestampedTransformPool.acquire().set(serverTimestamp, transformToBuffer, isActive));
            lastServerTimestamp = serverTimestamp;
            if (newTransform != null) {
                lastValidSnapshot.set(newTransform);
            }
        }
    }

    public void cleanupBuffer() {
        if (transformBuffer.isEmpty() || !isClockOffsetInitialized) {
            return;
        }

        long estimatedServerTimeNow = ClientClock.getInstance().getGameTimeNanos() + this.clockOffsetNanos;
        long timeHorizonNanos = estimatedServerTimeNow - (MAX_BUFFER_TIME_MS * 1_000_000L);

        while (transformBuffer.size() > MIN_BUFFER_FOR_INTERPOLATION) {
            TimestampedTransform first = transformBuffer.peekFirst();
            if (first != null && first.timestamp < timeHorizonNanos) {
                TimestampedTransformPool.release(transformBuffer.removeFirst());
            } else {
                break;
            }
        }
        while (transformBuffer.size() > MAX_BUFFER_SIZE) {
            TimestampedTransformPool.release(transformBuffer.removeFirst());
        }
    }

    public PhysicsTransform getRenderTransform(float partialTicks) {
        if (transformBuffer.isEmpty()) {
            return lastValidSnapshot;
        }
        TimestampedTransform latest = transformBuffer.peekLast();

        if (!latest.isActive || transformBuffer.size() < MIN_BUFFER_FOR_INTERPOLATION || !isClockOffsetInitialized) {
            return latest.transform;
        }

        long nowNanosClient = ClientClock.getInstance().getGameTimeNanos();
        long estimatedServerTimeNow = nowNanosClient + this.clockOffsetNanos;
        long renderTimestamp = estimatedServerTimeNow - (INTERPOLATION_DELAY_MS * 1_000_000L);

        TimestampedTransform before = null;
        TimestampedTransform after = null;

        for (TimestampedTransform current : transformBuffer) {
            if (current.timestamp <= renderTimestamp) {
                before = current;
            } else {
                after = current;
                break;
            }
        }

        if (before == null) {
            return transformBuffer.peekFirst().transform;
        }
        if (after == null) {
            return before.transform;
        }

        if (!before.isActive && after.isActive) {
            return before.transform;
        }

        long timeDiff = after.timestamp - before.timestamp;
        float alpha = (timeDiff <= 0) ? 1.0f : Mth.clamp((float) (renderTimestamp - before.timestamp) / timeDiff, 0.0f, 1.0f);

        PhysicsOperations.lerp(before.transform.getTranslation(), after.transform.getTranslation(), alpha, renderTransform.getTranslation());
        PhysicsOperations.slerp(before.transform.getRotation(), after.transform.getRotation(), alpha, renderTransform.getRotation());

        return renderTransform;
    }

    public void releaseAll() {
        transformBuffer.forEach(TimestampedTransformPool::release);
        transformBuffer.clear();
    }

    public UUID getId() {
        return id;
    }

    @Nullable
    public RigidPhysicsObject.Renderer getRenderer() {
        return renderer;
    }

    private static class TimestampedTransform {
        long timestamp;
        final PhysicsTransform transform = new PhysicsTransform();
        boolean isActive;

        public TimestampedTransform set(long timestamp, PhysicsTransform source, boolean isActive) {
            this.timestamp = timestamp;
            this.transform.set(source);
            this.isActive = isActive;
            return this;
        }

        public void reset() {
            this.timestamp = 0;
            this.isActive = false;
            this.transform.loadIdentity();
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