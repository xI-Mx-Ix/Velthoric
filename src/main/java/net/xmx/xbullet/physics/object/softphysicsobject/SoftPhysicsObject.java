package net.xmx.xbullet.physics.object.softphysicsobject;

import com.github.stephengold.joltjni.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.item.PhysicsRemoverItem;
import net.xmx.xbullet.math.PhysicsTransform;
import net.xmx.xbullet.physics.core.PhysicsWorld;
import net.xmx.xbullet.physics.object.global.physicsobject.AbstractPhysicsObject;
import net.xmx.xbullet.physics.object.global.physicsobject.EObjectType;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.PhysicsObjectManager;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.PhysicsObjectManagerRegistry;
import net.xmx.xbullet.physics.object.global.physicsobject.properties.IPhysicsObjectProperties;
import net.xmx.xbullet.physics.object.softphysicsobject.client.ClientSoftPhysicsObjectData;
import net.xmx.xbullet.physics.object.softphysicsobject.pcmd.AddSoftBodyCommand;
import net.xmx.xbullet.physics.object.softphysicsobject.pcmd.RemoveSoftBodyCommand;
import net.xmx.xbullet.physics.object.softphysicsobject.properties.SoftPhysicsObjectProperties;

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

        if (initialNbt != null && initialNbt.contains("softBodyProperties", 10)) {
            this.softProperties = SoftPhysicsObjectProperties.fromNbt(initialNbt.getCompound("softBodyProperties"));
        } else {
            this.softProperties = defaultProps;
        }
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
                if (lastSyncedVertexData != null) {
                    Vertex[] vertices = settingsTarget.getVertices();
                    if (vertices != null && vertices.length * 3 == lastSyncedVertexData.length) {
                        for (int i = 0; i < vertices.length; ++i) {
                            Vertex v = vertices[i];
                            if (v != null) {
                                v.setPosition(new Vec3(
                                        lastSyncedVertexData[i * 3],
                                        lastSyncedVertexData[i * 3 + 1],
                                        lastSyncedVertexData[i * 3 + 2]
                                ));
                            }
                        }
                    } else {
                        XBullet.LOGGER.warn("Vertex data length mismatch for soft body {}. Saved: {}, Expected: {}",
                                physicsId, lastSyncedVertexData.length, (vertices != null ? vertices.length * 3 : "null"));
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
    }

    @Override
    @Nullable
    public SoftBodyCreationSettings createSoftBodyCreationSettings() {
        throw new UnsupportedOperationException("Use getOrBuildSharedSettings() and configureSoftBodyCreationSettings() instead.");
    }

    @Override
    public void initializePhysics(PhysicsWorld physicsWorld) {
        if (physicsInitialized || isRemoved || level.isClientSide() || physicsWorld == null || !physicsWorld.isRunning()) return;
        AddSoftBodyCommand.queue(physicsWorld, this);
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
    public void serverTick(PhysicsWorld physicsWorld) { }

    @Override
    public void updateStateFromPhysicsThread(PhysicsTransform transform, Vec3 linearVelocity, Vec3 angularVelocity, @Nullable float[] softBodyVertices, boolean isActive) {
        if (this.isRemoved || level.isClientSide()) return;
        if (softBodyVertices != null) {
            this.lastSyncedVertexData = softBodyVertices;
        }
    }

    @Override
    public void onRightClickWithTool(Player player) {
        if (player.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof PhysicsRemoverItem && player.level() instanceof ServerLevel sl) {
            PhysicsObjectManager manager = PhysicsObjectManagerRegistry.getInstance().getManagerForLevel(sl);
            manager.removeObject(this.physicsId, true);
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
        if (tag.contains("vertexData", 9)) {
            ListTag list = tag.getList("vertexData", 5);
            lastSyncedVertexData = new float[list.size()];
            for (int i = 0; i < list.size(); ++i) {
                lastSyncedVertexData[i] = list.getFloat(i);
            }
        }
    }

    protected static ListTag newFloatList(float... values) {
        ListTag tag = new ListTag();
        for (float v : values) tag.add(FloatTag.valueOf(v));
        return tag;
    }

    public abstract static class Renderer {
        public abstract void render(ClientSoftPhysicsObjectData data, PoseStack poseStack, MultiBufferSource bufferSource, float partialTicks, int packedLight);
    }
}