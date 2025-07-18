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
    @Nullable private final RigidPhysicsObject.Renderer renderer;
    private final Deque<TimestampedTransform> transformBuffer = new ArrayDeque<>();
    private final PhysicsTransform lastValidSnapshot = new PhysicsTransform();
    private final PhysicsTransform renderTransform = new PhysicsTransform();
    private long lastServerTimestampNanos = 0;
    private long clockOffsetNanos = 0L;
    private boolean isClockOffsetInitialized = false;
    private byte[] customData;

    private static final long INTERPOLATION_DELAY_MS = 100;
    private static final int MAX_BUFFER_SIZE = 20;
    private static final long MAX_BUFFER_TIME_MS = 1000;
    private static final int MIN_BUFFER_FOR_INTERPOLATION = 2;
    private static final double OFFSET_SMOOTHING_FACTOR = 0.05;
    private static final float MAX_EXTRAPOLATION_SECONDS = 0.2f;
    private static final long SNAP_THRESHOLD_NANOS = 1_000_000_000L;

    public ClientRigidPhysicsObjectData(UUID id, @Nullable RigidPhysicsObject.Renderer renderer, long initialServerTimestampNanos) {
        this.id = id;
        this.renderer = renderer;
        this.lastValidSnapshot.loadIdentity();
        this.lastServerTimestampNanos = initialServerTimestampNanos;
    }

    public void readData(ByteBuf buffer) {
        FriendlyByteBuf buf = new FriendlyByteBuf(buffer);
        this.lastValidSnapshot.fromBuffer(buf);

        if (buf.readableBytes() > 0) {
            buf.skipBytes(28);
            if (buf.readableBytes() > 0) {
                buf.readUtf();
            }
            if (buf.readableBytes() > 0) {
                buf.skipBytes(24);
            }
        }
        if (buf.readableBytes() > 0) {
            this.customData = new byte[buf.readableBytes()];
            buf.readBytes(this.customData);
        } else {
            this.customData = new byte[0];
        }
        transformBuffer.addLast(TimestampedTransformPool.acquire().set(lastServerTimestampNanos, lastValidSnapshot, null, null, false));
    }

    public byte[] getCustomData() {
        return customData;
    }

    public void updateTransformFromServer(@Nullable PhysicsTransform newTransform, @Nullable Vec3 linVel, @Nullable Vec3 angVel, long serverTimestampNanos, boolean isActive) {
        if (newTransform == null || serverTimestampNanos <= 0) {
            return;
        }
        long clientReceiptTimeNanos = ClientClock.getInstance().getGameTimeNanos();
        if (!isClockOffsetInitialized) {
            this.clockOffsetNanos = serverTimestampNanos - clientReceiptTimeNanos;
            this.isClockOffsetInitialized = true;
        } else {
            long newOffset = serverTimestampNanos - clientReceiptTimeNanos;
            this.clockOffsetNanos = (long) (this.clockOffsetNanos * (1.0 - OFFSET_SMOOTHING_FACTOR) + newOffset * OFFSET_SMOOTHING_FACTOR);
        }
        if (serverTimestampNanos > lastServerTimestampNanos) {
            if (!transformBuffer.isEmpty() && (serverTimestampNanos - lastServerTimestampNanos > SNAP_THRESHOLD_NANOS)) {
                releaseAll();
            }
            transformBuffer.addLast(TimestampedTransformPool.acquire().set(serverTimestampNanos, newTransform, linVel, angVel, isActive));
            lastServerTimestampNanos = serverTimestampNanos;
            lastValidSnapshot.set(newTransform);
        }
    }

    public void cleanupBuffer() {
        if (transformBuffer.isEmpty() || !isClockOffsetInitialized) {
            return;
        }
        long estimatedServerTimeNow = ClientClock.getInstance().getGameTimeNanos() + this.clockOffsetNanos;
        long timeHorizonNanos = estimatedServerTimeNow - (MAX_BUFFER_TIME_MS * 1_000_000L);
        while (transformBuffer.size() > MIN_BUFFER_FOR_INTERPOLATION && transformBuffer.peekFirst().timestampNanos < timeHorizonNanos) {
            TimestampedTransformPool.release(transformBuffer.removeFirst());
        }
        while (transformBuffer.size() > MAX_BUFFER_SIZE) {
            TimestampedTransformPool.release(transformBuffer.removeFirst());
        }
    }

    public PhysicsTransform getRenderTransform(float partialTicks) {
        if (transformBuffer.size() < MIN_BUFFER_FOR_INTERPOLATION || !isClockOffsetInitialized) {
            return transformBuffer.isEmpty() ? lastValidSnapshot : transformBuffer.peekLast().transform;
        }

        long estimatedServerTimeNow = ClientClock.getInstance().getGameTimeNanos() + this.clockOffsetNanos;
        long renderTimestamp = estimatedServerTimeNow - (INTERPOLATION_DELAY_MS * 1_000_000L);

        TimestampedTransform before = null;
        TimestampedTransform after = null;
        for (TimestampedTransform current : transformBuffer) {
            if (current.timestampNanos <= renderTimestamp) {
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

        long timeDiffNanos = after.timestampNanos - before.timestampNanos;
        if (timeDiffNanos <= 0 || timeDiffNanos > SNAP_THRESHOLD_NANOS) {
            return after.transform;
        }

        float dtSeconds = timeDiffNanos / 1_000_000_000f;
        float alpha = Mth.clamp((float) (renderTimestamp - before.timestampNanos) / timeDiffNanos, 0.0f, 1.0f);

        Vec3 beforeLinVel = before.linearVelocity;
        Vec3 beforeAngVel = before.angularVelocity;
        if (!before.isActive && after.isActive) {
            beforeLinVel = Vec3.sZero();
            beforeAngVel = Vec3.sZero();
        }

        PhysicsOperations.cubicHermite(
                before.transform.getTranslation(), beforeLinVel,
                after.transform.getTranslation(), after.linearVelocity,
                alpha, dtSeconds, renderTransform.getTranslation()
        );
        PhysicsOperations.interpolateCubic(
                before.transform.getRotation(), beforeAngVel,
                after.transform.getRotation(), after.angularVelocity,
                dtSeconds, alpha, renderTransform.getRotation()
        );
        return renderTransform;
    }

    public void releaseAll() {
        transformBuffer.forEach(TimestampedTransformPool::release);
        transformBuffer.clear();
    }

    public UUID getId() { return id; }
    @Nullable public RigidPhysicsObject.Renderer getRenderer() { return renderer; }

    private static class TimestampedTransform {
        long timestampNanos;
        final PhysicsTransform transform = new PhysicsTransform();
        final Vec3 linearVelocity = new Vec3();
        final Vec3 angularVelocity = new Vec3();
        boolean isActive;

        public TimestampedTransform set(long timestampNanos, PhysicsTransform source, @Nullable Vec3 linVel, @Nullable Vec3 angVel, boolean isActive) {
            this.timestampNanos = timestampNanos;
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