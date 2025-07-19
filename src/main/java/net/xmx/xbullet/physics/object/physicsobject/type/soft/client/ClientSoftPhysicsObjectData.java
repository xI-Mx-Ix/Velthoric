package net.xmx.xbullet.physics.object.physicsobject.type.soft.client;

import com.github.stephengold.joltjni.Vec3;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.xbullet.math.PhysicsTransform;
import net.xmx.xbullet.physics.object.physicsobject.client.interpolation.InterpolationController;
import net.xmx.xbullet.physics.object.physicsobject.client.interpolation.StateSnapshot;
import net.xmx.xbullet.physics.object.physicsobject.type.soft.SoftPhysicsObject;
import net.xmx.xbullet.physics.object.physicsobject.type.soft.properties.SoftPhysicsObjectProperties;

import javax.annotation.Nullable;
import java.util.UUID;

public class ClientSoftPhysicsObjectData {
    private final UUID id;
    @Nullable private final SoftPhysicsObject.Renderer renderer;
    private final InterpolationController interpolationController = new InterpolationController();
    private final long initialServerTimestamp;
    private byte[] customData;
    @Nullable private float[] latestSyncedVertexData = null;
    private final PhysicsTransform initialTransform = new PhysicsTransform();

    private final Vec3 initialLinearVelocity = new Vec3();
    private final Vec3 initialAngularVelocity = new Vec3();

    public ClientSoftPhysicsObjectData(UUID id, @Nullable SoftPhysicsObject.Renderer renderer, long initialTimestampNanos) {
        this.id = id;
        this.renderer = renderer;
        this.initialServerTimestamp = initialTimestampNanos;
    }

    public void readData(ByteBuf buffer) {
        FriendlyByteBuf buf = new FriendlyByteBuf(buffer);
        initialTransform.fromBuffer(buf);

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

        // Initialize interpolation controller with spawn data immediately
        // This ensures we have at least one state for interpolation
        interpolationController.addState(this.initialServerTimestamp, initialTransform, initialLinearVelocity, initialAngularVelocity, this.latestSyncedVertexData, true);
    }

    public void updateDataFromServer(@Nullable PhysicsTransform transform, @Nullable Vec3 linearVel, @Nullable Vec3 angularVel, @Nullable float[] newVertexData, long serverTimestampNanos, boolean isActive) {
        if (serverTimestampNanos <= 0 || transform == null) return;

        // Remove the firstUpdate logic since we now initialize in readData()
        if (newVertexData != null) {
            this.latestSyncedVertexData = newVertexData;
        }

        interpolationController.addState(serverTimestampNanos, transform, linearVel, angularVel, this.latestSyncedVertexData, isActive);
    }

    @Nullable
    public float[] getRenderVertexData(float partialTicks) {
        StateSnapshot interpolated = interpolationController.getInterpolatedState(partialTicks);
        return interpolated != null ? interpolated.vertexData : latestSyncedVertexData;
    }

    @Nullable
    public PhysicsTransform getRenderTransform(float partialTicks) {
        StateSnapshot interpolated = interpolationController.getInterpolatedState(partialTicks);

        if (interpolated == null) {
            return initialTransform;
        }
        return interpolated.transform;
    }

    public void releaseAll() {
        interpolationController.release();
    }

    public byte[] getCustomData() {
        return customData;
    }

    @Nullable
    public float[] getLatestVertexData() {
        return latestSyncedVertexData;
    }

    public UUID getId() {
        return id;
    }

    @Nullable
    public SoftPhysicsObject.Renderer getRenderer() {
        return renderer;
    }
}