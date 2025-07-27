package net.xmx.vortex.physics.object.physicsobject.type.soft;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.operator.Op;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.xmx.vortex.init.VxMainClass;
import net.xmx.vortex.item.PhysicsRemoverItem;
import net.xmx.vortex.math.VxTransform;
import net.xmx.vortex.physics.object.physicsobject.AbstractPhysicsObject;
import net.xmx.vortex.physics.object.physicsobject.EObjectType;
import net.xmx.vortex.physics.object.physicsobject.manager.VxObjectManager;
import net.xmx.vortex.physics.object.physicsobject.properties.IPhysicsObjectProperties;
import net.xmx.vortex.physics.object.physicsobject.type.soft.client.ClientSoftPhysicsObjectData;
import net.xmx.vortex.physics.object.physicsobject.type.soft.pcmd.AddSoftBodyCommand;
import net.xmx.vortex.physics.object.physicsobject.type.soft.pcmd.RemoveSoftBodyCommand;
import net.xmx.vortex.physics.object.physicsobject.type.soft.properties.SoftPhysicsObjectProperties;
import net.xmx.vortex.physics.world.VxPhysicsWorld;

import javax.annotation.Nullable;
import java.util.UUID;

public abstract class SoftPhysicsObject extends AbstractPhysicsObject {

    public float[] lastSyncedVertexData;
    protected SoftPhysicsObjectProperties softProperties;
    protected SoftBodySharedSettingsRef sharedSettingsRef;

    protected SoftPhysicsObject(UUID physicsId, Level level, String objectTypeIdentifier, VxTransform initialTransform, IPhysicsObjectProperties properties) {
        super(physicsId, level, objectTypeIdentifier, initialTransform, properties);

        if (!(properties instanceof SoftPhysicsObjectProperties defaultProps)) {
            throw new IllegalArgumentException("SoftPhysicsObject requires SoftPhysicsObjectProperties");
        }
        this.softProperties = defaultProps;
    }

    @Override
    protected final void addBodySpecificData(FriendlyByteBuf buf) {
        this.softProperties.toBuffer(buf);
        boolean hasVertices = lastSyncedVertexData != null && lastSyncedVertexData.length > 0;
        buf.writeBoolean(hasVertices);
        if (hasVertices) {
            buf.writeVarInt(lastSyncedVertexData.length);
            for (float v : lastSyncedVertexData) {
                buf.writeFloat(v);
            }
        }

        addAdditionalData(buf);
    }

    @Override
    protected final void readBodySpecificData(FriendlyByteBuf buf) {
        this.softProperties = SoftPhysicsObjectProperties.fromBuffer(buf);
        if (buf.readBoolean()) {
            int length = buf.readVarInt();
            this.lastSyncedVertexData = new float[length];
            for (int i = 0; i < length; i++) {
                this.lastSyncedVertexData[i] = buf.readFloat();
            }
        } else {
            this.lastSyncedVertexData = null;
        }

        readAdditionalData(buf);
    }

    protected void addAdditionalData(FriendlyByteBuf buf) {}

    protected void readAdditionalData(FriendlyByteBuf buf) {}

    @Override
    public EObjectType getPhysicsObjectType() {
        return EObjectType.SOFT_BODY;
    }

    protected abstract SoftBodySharedSettings buildSharedSettings();

    @SuppressWarnings("resource")
    public SoftBodySharedSettings getOrBuildSharedSettings() {
        if (this.sharedSettingsRef == null) {
            SoftBodySharedSettings settingsTarget = buildSharedSettings();
            if (settingsTarget != null) {
                if (lastSyncedVertexData != null && lastSyncedVertexData.length > 0) {
                    Vertex[] vertices = settingsTarget.getVertices();
                    if (vertices != null && vertices.length * 3 == lastSyncedVertexData.length) {
                        RVec3 bodyPosition = this.currentTransform.getTranslation();
                        Quat bodyRotation = this.currentTransform.getRotation();
                        Quat invRotation = bodyRotation.conjugated();
                        for (int i = 0; i < vertices.length; ++i) {
                            Vertex v = vertices[i];
                            if (v != null) {
                                RVec3 worldVertexPos = new RVec3(lastSyncedVertexData[i * 3], lastSyncedVertexData[i * 3 + 1], lastSyncedVertexData[i * 3 + 2]);
                                RVec3 relativePos = Op.minus(worldVertexPos, bodyPosition);
                                Vec3 localPos = Op.star(invRotation, relativePos.toVec3());
                                v.setPosition(localPos);
                            }
                        }
                    } else if (vertices != null) {
                        VxMainClass.LOGGER.warn("Vertex data length mismatch for soft body {}. Saved: {}, Expected: {}", physicsId, lastSyncedVertexData.length, vertices.length * 3);
                    }
                }
                this.sharedSettingsRef = settingsTarget.toRef();
            }
        }
        return (this.sharedSettingsRef != null) ? this.sharedSettingsRef.getPtr() : null;
    }

    public final void configureSoftBodyCreationSettings(SoftBodyCreationSettings settings) {
        settings.setFriction(softProperties.getFriction());
        settings.setRestitution(softProperties.getRestitution());
        settings.setLinearDamping(softProperties.getLinearDamping());
        settings.setGravityFactor(softProperties.getGravityFactor());
        settings.setPressure(softProperties.getPressure());
        settings.setNumIterations(softProperties.getNumIterations());
        configureAdditionalSoftBodyCreationSettings(settings);
    }

    protected void configureAdditionalSoftBodyCreationSettings(SoftBodyCreationSettings settings) {
    }

    @Override
    public void initializePhysics(VxPhysicsWorld physicsWorld) {
        if (physicsInitialized || isRemoved || level.isClientSide() || physicsWorld == null || !physicsWorld.isRunning()) {
            return;
        }
        physicsWorld.queueCommand(new AddSoftBodyCommand(this.physicsId, true));
    }

    @Override
    public void removeFromPhysics(VxPhysicsWorld physicsWorld) {
        if (bodyId == 0 || physicsWorld == null || !physicsWorld.isRunning()) {
            return;
        }
        RemoveSoftBodyCommand.queue(physicsWorld, this.physicsId, this.bodyId);
        if (this.sharedSettingsRef != null) {
            this.sharedSettingsRef.close();
            this.sharedSettingsRef = null;
        }
    }

    @Override
    @Nullable
    public float[] getLastSyncedVertexData() {
        return this.lastSyncedVertexData;
    }

    @Override
    public void gameTick(ServerLevel serverLevel) {
    }

    @Override
    public void physicsTick(VxPhysicsWorld physicsWorld) {
    }

    @Override
    public void updateStateFromPhysicsThread(long timestampNanos, @Nullable VxTransform transform, @Nullable Vec3 linearVelocity, @Nullable Vec3 angularVelocity, @Nullable float[] softBodyVertices, boolean isActive) {
        super.updateStateFromPhysicsThread(timestampNanos, transform, linearVelocity, angularVelocity, softBodyVertices, isActive);
        if (isActive) {
            if (softBodyVertices != null) {
                this.lastSyncedVertexData = softBodyVertices;
            }
        }
    }

    @Override
    public void onRightClickWithTool(ServerPlayer player) {
        if (player.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof PhysicsRemoverItem && player.level() instanceof ServerLevel sl) {
            VxObjectManager manager = VxPhysicsWorld.getObjectManager(sl.dimension());
            if (manager != null) {
                manager.deleteObject(this.physicsId);
            }
        }
    }

    public abstract static class Renderer {
        public abstract void render(ClientSoftPhysicsObjectData data, PoseStack poseStack, MultiBufferSource bufferSource, float partialTicks, int packedLight);
    }


    @Override
    public final void fixedGameTick(ServerLevel level) {
        if (ridingProxy != null) {
            VxMainClass.LOGGER.warn("Soft Bodies cannot be ridden.");
            ridingProxy.kill();
            ridingProxy = null;
        }
    }

    @Override
    public final void fixedPhysicsTick(VxPhysicsWorld physicsWorld) {
    }
}