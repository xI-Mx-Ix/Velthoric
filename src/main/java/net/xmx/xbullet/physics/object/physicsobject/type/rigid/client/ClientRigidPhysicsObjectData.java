package net.xmx.xbullet.physics.object.physicsobject.type.rigid.client;

import com.github.stephengold.joltjni.Vec3;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.xbullet.math.PhysicsTransform;
import net.xmx.xbullet.physics.object.physicsobject.client.interpolation.InterpolationController;
import net.xmx.xbullet.physics.object.physicsobject.client.interpolation.StateSnapshot;
import net.xmx.xbullet.physics.object.physicsobject.type.rigid.RigidPhysicsObject;

import javax.annotation.Nullable;
import java.util.UUID;

public class ClientRigidPhysicsObjectData {
    private final UUID id;
    @Nullable private final RigidPhysicsObject.Renderer renderer;
    private final InterpolationController interpolationController = new InterpolationController();
    private final long initialServerTimestamp;
    private byte[] customData;
    private boolean firstUpdate = true;
    private final PhysicsTransform initialTransform = new PhysicsTransform();

    private final Vec3 initialLinearVelocity = new Vec3();
    private final Vec3 initialAngularVelocity = new Vec3();

    public ClientRigidPhysicsObjectData(UUID id, @Nullable RigidPhysicsObject.Renderer renderer, long initialServerTimestampNanos) {
        this.id = id;
        this.renderer = renderer;
        this.initialServerTimestamp = initialServerTimestampNanos;
    }

    public void readData(ByteBuf buffer) {
        FriendlyByteBuf buf = new FriendlyByteBuf(buffer);
        initialTransform.fromBuffer(buf);

        if (buf.readableBytes() > 0) {
            buf.skipBytes(28);
            if (buf.readableBytes() > 0) {
                buf.readUtf();
            }
            if (buf.readableBytes() > 0) {
                this.initialLinearVelocity.set(buf.readFloat(), buf.readFloat(), buf.readFloat());
                this.initialAngularVelocity.set(buf.readFloat(), buf.readFloat(), buf.readFloat());
            }
        }
        if (buf.readableBytes() > 0) {
            this.customData = new byte[buf.readableBytes()];
            buf.readBytes(this.customData);
        } else {
            this.customData = new byte[0];
        }

        interpolationController.addState(this.initialServerTimestamp, initialTransform, this.initialLinearVelocity, this.initialAngularVelocity, null, true);
    }

    public byte[] getCustomData() {
        return customData;
    }

    public void updateTransformFromServer(@Nullable PhysicsTransform newTransform, @Nullable Vec3 linVel, @Nullable Vec3 angVel, long serverTimestampNanos, boolean isActive) {
        if (newTransform == null || serverTimestampNanos <= 0) return;

        interpolationController.addState(serverTimestampNanos, newTransform, linVel, angVel, null, isActive);
    }

    @Nullable
    public PhysicsTransform getRenderTransform(float partialTicks) {
        StateSnapshot interpolated = interpolationController.getInterpolatedState(partialTicks);
        if (interpolated == null) {
            return this.initialTransform;
        }
        return interpolated.transform;
    }

    public void releaseAll() {
        interpolationController.release();
    }

    public UUID getId() {
        return id;
    }

    @Nullable
    public RigidPhysicsObject.Renderer getRenderer() {
        return renderer;
    }
}