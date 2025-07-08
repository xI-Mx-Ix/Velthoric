package net.xmx.xbullet.physics.object.physicsobject.type.rigid.client;

import com.github.stephengold.joltjni.Vec3;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.xmx.xbullet.math.PhysicsOperations;
import net.xmx.xbullet.math.PhysicsTransform;
import net.xmx.xbullet.physics.object.physicsobject.type.rigid.RigidPhysicsObject;
import net.xmx.xbullet.physics.world.time.ClientClock;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

public class ClientRigidPhysicsObjectData {
    private final UUID id;
    @Nullable private final RigidPhysicsObject.Renderer renderer;

    private static final long INTERPOLATION_DELAY_MS = 100;
    private static final int MAX_BUFFER_SIZE = 20;
    private static final long MAX_BUFFER_TIME_MS = 2000;
    private static final int MIN_BUFFER_FOR_INTERPOLATION = 2;

    private final Deque<TimestampedTransform> transformBuffer = new ArrayDeque<>();
    private final PhysicsTransform lastValidSnapshot = new PhysicsTransform();
    private CompoundTag syncedNbtData = new CompoundTag();

    private final PhysicsTransform renderTransform = new PhysicsTransform();
    private long lastServerTimestamp = 0;

    private long clockOffsetNanos = 0L;
    private boolean isClockOffsetInitialized = false;
    private static final double OFFSET_SMOOTHING_FACTOR = 0.05;

    public ClientRigidPhysicsObjectData(UUID id, PhysicsTransform initialTransform,
                                        float mass, float friction, float restitution,
                                        float linearDamping, float angularDamping,
                                        @Nullable RigidPhysicsObject.Renderer renderer, long initialServerTimestampNanos) {
        this.id = id;
        this.renderer = renderer;

        if (initialTransform != null) {
            this.lastValidSnapshot.set(initialTransform);
        } else {
            this.lastValidSnapshot.loadIdentity();
        }

        this.transformBuffer.addLast(new TimestampedTransform(initialServerTimestampNanos, lastValidSnapshot, new Vec3(), new Vec3(), true));
        this.lastServerTimestamp = initialServerTimestampNanos;
    }

    public void updateTransformFromServer(@Nullable PhysicsTransform newTransform, @Nullable Vec3 linVel, @Nullable Vec3 angVel, long serverTimestamp, boolean isActive) {
        PhysicsTransform transformToBuffer = newTransform != null ? newTransform : lastValidSnapshot;

        if (transformToBuffer == null || serverTimestamp <= 0) return;

        long clientReceiptTimeNanos = ClientClock.getInstance().getGameTimeNanos();
        if (!isClockOffsetInitialized) {
            this.clockOffsetNanos = serverTimestamp - clientReceiptTimeNanos;
            this.isClockOffsetInitialized = true;
        } else {
            long newOffset = serverTimestamp - clientReceiptTimeNanos;
            this.clockOffsetNanos = (long) (this.clockOffsetNanos * (1.0 - OFFSET_SMOOTHING_FACTOR) + newOffset * OFFSET_SMOOTHING_FACTOR);
        }

        if (serverTimestamp > lastServerTimestamp) {
            transformBuffer.addLast(new TimestampedTransform(serverTimestamp, transformToBuffer, linVel, angVel, isActive));
            lastServerTimestamp = serverTimestamp;

            if (newTransform != null) {
                lastValidSnapshot.set(newTransform);
            }
        }
    }

    public void updateNbt(CompoundTag nbt) {
        this.syncedNbtData = nbt.copy();
    }

    public void cleanupBuffer() {
        if (transformBuffer.isEmpty() || !isClockOffsetInitialized) return;

        long estimatedServerTimeNow = ClientClock.getInstance().getGameTimeNanos() + this.clockOffsetNanos;
        long timeHorizonNanos = estimatedServerTimeNow - (MAX_BUFFER_TIME_MS * 1_000_000L);

        while (transformBuffer.size() > MIN_BUFFER_FOR_INTERPOLATION) {
            TimestampedTransform first = transformBuffer.peekFirst();
            if (first != null && first.timestamp < timeHorizonNanos) {
                transformBuffer.removeFirst();
            } else {
                break;
            }
        }
        while (transformBuffer.size() > MAX_BUFFER_SIZE) {
            transformBuffer.removeFirst();
        }
    }

    public PhysicsTransform getRenderTransform(float partialTicks) {
        if (transformBuffer.isEmpty() || !isClockOffsetInitialized) {
            return lastValidSnapshot;
        }

        if (transformBuffer.size() < MIN_BUFFER_FOR_INTERPOLATION) {
            return transformBuffer.peekFirst().transform;
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

        long timeDiff = after.timestamp - before.timestamp;
        float alpha = (timeDiff <= 0) ? 1.0f : (float)(renderTimestamp - before.timestamp) / timeDiff;
        alpha = Mth.clamp(alpha, 0.0f, 1.0f);

        PhysicsOperations.lerp(before.transform.getTranslation(), after.transform.getTranslation(), alpha, renderTransform.getTranslation());
        PhysicsOperations.slerp(before.transform.getRotation(), after.transform.getRotation(), alpha, renderTransform.getRotation());

        return renderTransform;
    }

    public UUID getId() { return id; }
    public CompoundTag getSyncedNbtData() { return syncedNbtData.copy(); }
    @Nullable public RigidPhysicsObject.Renderer getRenderer() { return renderer; }

    private static class TimestampedTransform {
        final long timestamp;
        final PhysicsTransform transform;

        final Vec3 linearVelocity;
        final Vec3 angularVelocity;
        final boolean isActive;

        TimestampedTransform(long timestamp, PhysicsTransform source, @Nullable Vec3 linVel, @Nullable Vec3 angVel, boolean isActive) {
            this.timestamp = timestamp;
            this.transform = source.copy();
            this.linearVelocity = linVel != null ? new Vec3(linVel) : new Vec3();
            this.angularVelocity = angVel != null ? new Vec3(angVel) : new Vec3();
            this.isActive = isActive;
        }
    }
}