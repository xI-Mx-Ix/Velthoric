package net.xmx.xbullet.physics.object.physicsobject.type.soft;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.operator.Op;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.item.PhysicsRemoverItem;
import net.xmx.xbullet.math.PhysicsTransform;
import net.xmx.xbullet.physics.object.physicsobject.manager.ObjectManager;
import net.xmx.xbullet.physics.world.PhysicsWorld;
import net.xmx.xbullet.physics.object.physicsobject.AbstractPhysicsObject;
import net.xmx.xbullet.physics.object.physicsobject.EObjectType;
import net.xmx.xbullet.physics.object.physicsobject.properties.IPhysicsObjectProperties;
import net.xmx.xbullet.physics.object.physicsobject.type.soft.client.ClientSoftPhysicsObjectData;
import net.xmx.xbullet.physics.object.physicsobject.type.soft.pcmd.AddSoftBodyCommand;
import net.xmx.xbullet.physics.object.physicsobject.type.soft.pcmd.RemoveSoftBodyCommand;
import net.xmx.xbullet.physics.object.physicsobject.type.soft.properties.SoftPhysicsObjectProperties;

import javax.annotation.Nullable;
import java.util.UUID;

public abstract class SoftPhysicsObject extends AbstractPhysicsObject {

    public float[] lastSyncedVertexData;
    protected SoftPhysicsObjectProperties softProperties;
    protected SoftBodySharedSettingsRef sharedSettingsRef;

    protected SoftPhysicsObject(UUID physicsId, Level level, String objectTypeIdentifier, PhysicsTransform initialTransform, IPhysicsObjectProperties properties, @Nullable CompoundTag initialNbt) {
        super(physicsId, level, objectTypeIdentifier, initialTransform, properties, initialNbt);

        if (!(properties instanceof SoftPhysicsObjectProperties defaultProps)) {
            throw new IllegalArgumentException("SoftPhysicsObject requires SoftPhysicsObjectProperties");
        }

        this.softProperties = defaultProps;
    }

    @Override
    public EObjectType getPhysicsObjectType() {
        return EObjectType.SOFT_BODY;
    }

    protected abstract SoftBodySharedSettings buildSharedSettings();

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
                                RVec3 worldVertexPos = new RVec3(
                                        (double)lastSyncedVertexData[i * 3],
                                        (double)lastSyncedVertexData[i * 3 + 1],
                                        (double)lastSyncedVertexData[i * 3 + 2]
                                );

                                RVec3 relativePos = Op.minus(worldVertexPos, bodyPosition);
                                Vec3 localPos = Op.star(invRotation, relativePos.toVec3());
                                v.setPosition(localPos);
                            }
                        }
                    } else if (vertices != null) {
                        XBullet.LOGGER.warn("Vertex data length mismatch for soft body {}. Saved: {}, Expected: {}",
                                physicsId, lastSyncedVertexData.length, vertices.length * 3);
                    }
                }
                this.sharedSettingsRef = settingsTarget.toRef();
            }
        }
        return (this.sharedSettingsRef != null) ? this.sharedSettingsRef.getPtr() : null;
    }

    public void configureSoftBodyCreationSettings(SoftBodyCreationSettings settings) {
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
    @Nullable
    public SoftBodyCreationSettings createSoftBodyCreationSettings() {
        throw new UnsupportedOperationException("Use getOrBuildSharedSettings() and configureSoftBodyCreationSettings() instead.");
    }

    @Override
    public void initializePhysics(PhysicsWorld physicsWorld) {
        if (physicsInitialized || isRemoved || level.isClientSide() || physicsWorld == null || !physicsWorld.isRunning()) return;
        AddSoftBodyCommand.queue(physicsWorld, this, true);
    }

    @Override
    public void removeFromPhysics(PhysicsWorld physicsWorld) {
        if (bodyId == 0 || physicsWorld == null || !physicsWorld.isRunning()) return;
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
    public void physicsTick(PhysicsWorld physicsWorld) {
    }

    @Override
    public synchronized void updateStateFromPhysicsThread(long timestampNanos, @Nullable PhysicsTransform transform, @Nullable Vec3 linearVelocity, @Nullable Vec3 angularVelocity, @Nullable float[] softBodyVertices, boolean isActive) {
        if (this.isRemoved || this.level.isClientSide()) return;

        this.lastUpdateTimestampNanos = timestampNanos;
        this.isActive = isActive;

        if (isActive) {
            if (transform != null) this.currentTransform.set(transform);
            if (linearVelocity != null) this.lastSyncedLinearVel.set(linearVelocity);
            if (angularVelocity != null) this.lastSyncedAngularVel.set(angularVelocity);
            if (softBodyVertices != null) this.lastSyncedVertexData = softBodyVertices;
        } else {
            this.lastSyncedLinearVel.loadZero();
            this.lastSyncedAngularVel.loadZero();
        }
    }

    @Override
    public void onRightClickWithTool(Player player) {
        if (player.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof PhysicsRemoverItem && player.level() instanceof ServerLevel sl) {
            ObjectManager manager = PhysicsWorld.getObjectManager(sl.dimension());
            manager.deleteObject(this.physicsId);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (lastSyncedVertexData != null && lastSyncedVertexData.length > 0) {
            tag.put("vertexData", newFloatList(lastSyncedVertexData));
        }
        tag.put("softBodyProperties", this.softProperties.toNbt(new CompoundTag()));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("softBodyProperties", 10)) {
            this.softProperties = SoftPhysicsObjectProperties.fromNbt(tag.getCompound("softBodyProperties"));
        }

        if (tag.contains("vertexData", 9)) {
            ListTag list = tag.getList("vertexData", 5);
            lastSyncedVertexData = new float[list.size()];
            for (int i = 0; i < list.size(); ++i) {
                lastSyncedVertexData[i] = list.getFloat(i);
            }
        }
    }

    public abstract static class Renderer {
        public abstract void render(ClientSoftPhysicsObjectData data, PoseStack poseStack, MultiBufferSource bufferSource, float partialTicks, int packedLight);
    }
}