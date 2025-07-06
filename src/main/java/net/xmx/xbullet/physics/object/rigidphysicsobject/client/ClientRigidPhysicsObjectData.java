package net.xmx.xbullet.physics.object.rigidphysicsobject.client;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.xmx.xbullet.math.PhysicsOperations;
import net.xmx.xbullet.math.PhysicsTransform;
import net.xmx.xbullet.physics.object.rigidphysicsobject.RigidPhysicsObject;
import net.xmx.xbullet.physics.world.time.ClientClock;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

public class ClientRigidPhysicsObjectData {
    private final UUID id;
    @Nullable private final RigidPhysicsObject.Renderer renderer;

    private static final long INTERPOLATION_DELAY_MS = 100;
    private static final int MAX_BUFFER_SIZE = 250;
    private static final long MAX_BUFFER_TIME_MS = 2000;
    private static final int MIN_BUFFER_FOR_INTERPOLATION = 2;
    private static final long EXTRAPOLATION_MAX_TIME_MS = 500;

    private final Vec3 lastKnownLinearVelocity = new Vec3();
    private final Vec3 lastKnownAngularVelocity = new Vec3();

    private long clockOffsetNanos = 0L;
    private boolean isClockOffsetInitialized = false;
    private static final double OFFSET_SMOOTHING_FACTOR = 0.05;

    private final Deque<TimestampedTransform> transformBuffer = new ArrayDeque<>();
    private final PhysicsTransform lastValidSnapshot = new PhysicsTransform();
    private CompoundTag syncedNbtData = new CompoundTag();

    private final PhysicsTransform renderTransform = new PhysicsTransform();
    private long lastServerTimestamp = 0;

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

    public void updateTransformFromServer(PhysicsTransform newTransform, @Nullable Vec3 linVel, @Nullable Vec3 angVel, long serverTimestamp, boolean isActive) {
        if (newTransform == null || serverTimestamp <= 0) return;

        long clientReceiptTimeNanos = ClientClock.getInstance().getGameTimeNanos();
        if (!isClockOffsetInitialized) {
            this.clockOffsetNanos = serverTimestamp - clientReceiptTimeNanos;
            this.isClockOffsetInitialized = true;
        } else {
            long newOffset = serverTimestamp - clientReceiptTimeNanos;
            this.clockOffsetNanos = (long) (this.clockOffsetNanos * (1.0 - OFFSET_SMOOTHING_FACTOR) + newOffset * OFFSET_SMOOTHING_FACTOR);
        }

        if (serverTimestamp > lastServerTimestamp) {
            transformBuffer.addLast(new TimestampedTransform(serverTimestamp, newTransform, linVel, angVel, isActive));
            lastServerTimestamp = serverTimestamp;
            lastValidSnapshot.set(newTransform);

            if (linVel != null) {
                this.lastKnownLinearVelocity.set(linVel);
            } else {
                this.lastKnownLinearVelocity.loadZero();
            }
            if (angVel != null) {
                this.lastKnownAngularVelocity.set(angVel);
            } else {
                this.lastKnownAngularVelocity.loadZero();
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
        if (transformBuffer.size() < MIN_BUFFER_FOR_INTERPOLATION || !isClockOffsetInitialized) {
            return lastValidSnapshot;
        }

        long nowNanosClient = ClientClock.getInstance().getGameTimeNanos();
        long estimatedServerTimeNow = nowNanosClient + this.clockOffsetNanos;
        long renderTimestamp = estimatedServerTimeNow - (INTERPOLATION_DELAY_MS * 1_000_000L);

        TimestampedTransform latest = transformBuffer.peekLast();
        if (latest != null && !latest.isActive) {
            return latest.transform;
        }

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
            return transformBuffer.isEmpty() ? lastValidSnapshot : transformBuffer.peekFirst().transform;
        }
        if (after == null) {
            return calculateExtrapolatedTransform(before, renderTimestamp);
        }

        long timeDiff = after.timestamp - before.timestamp;
        float alpha = (timeDiff <= 0) ? 1.0f : (float)(renderTimestamp - before.timestamp) / timeDiff;
        alpha = Mth.clamp(alpha, 0.0f, 1.0f);

        PhysicsOperations.lerp(before.transform.getTranslation(), after.transform.getTranslation(), alpha, renderTransform.getTranslation());
        PhysicsOperations.slerp(before.transform.getRotation(), after.transform.getRotation(), alpha, renderTransform.getRotation());

        return renderTransform;
    }

    private PhysicsTransform calculateExtrapolatedTransform(TimestampedTransform latest, long renderTimestamp) {
        long extrapTimeNanos = renderTimestamp - latest.timestamp;
        if (!latest.isActive || extrapTimeNanos <= 0 || extrapTimeNanos > EXTRAPOLATION_MAX_TIME_MS * 1_000_000L) {
            return latest.transform;
        }

        float dt = (float)extrapTimeNanos / 1_000_000_000.0f;

        RVec3 startPos = latest.transform.getTranslation();
        Vec3 linVel = latest.linearVelocity;
        renderTransform.getTranslation().set(
                startPos.xx() + linVel.getX() * dt,
                startPos.yy() + linVel.getY() * dt,
                startPos.zz() + linVel.getZ() * dt
        );

        Quat startRot = latest.transform.getRotation();
        Vec3 angVel = latest.angularVelocity;
        PhysicsOperations.extrapolateRotation(startRot, angVel, dt, renderTransform.getRotation());

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

        TimestampedTransform(long timestamp, PhysicsTransform source, Vec3 linVel, Vec3 angVel, boolean isActive) {
            this.timestamp = timestamp;
            this.transform = source.copy();
            this.linearVelocity = linVel != null ? new Vec3(linVel) : new Vec3();
            this.angularVelocity = angVel != null ? new Vec3(angVel) : new Vec3();
            this.isActive = isActive;
        }
    }
}