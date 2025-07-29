package net.xmx.vortex.physics.object.physicsobject.type.soft;

import com.github.stephengold.joltjni.SoftBodyCreationSettings;
import com.github.stephengold.joltjni.SoftBodySharedSettings;
import com.github.stephengold.joltjni.SoftBodySharedSettingsRef;
import com.github.stephengold.joltjni.Vec3;
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
import net.xmx.vortex.physics.object.physicsobject.PhysicsObjectType;
import net.xmx.vortex.physics.object.physicsobject.manager.VxObjectManager;
import net.xmx.vortex.physics.object.physicsobject.manager.VxRemovalReason;
import net.xmx.vortex.physics.object.physicsobject.type.soft.client.ClientSoftPhysicsObjectData;
import net.xmx.vortex.physics.object.physicsobject.type.soft.pcmd.AddSoftBodyCommand;
import net.xmx.vortex.physics.object.physicsobject.type.soft.pcmd.RemoveSoftBodyCommand;
import net.xmx.vortex.physics.object.physicsobject.type.soft.properties.SoftPhysicsObjectProperties;
import net.xmx.vortex.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;

public abstract class SoftPhysicsObject extends AbstractPhysicsObject {

    protected final PhysicsObjectType<? extends SoftPhysicsObject> type;
    @Nullable
    protected float[] lastSyncedVertexData;
    protected SoftPhysicsObjectProperties softProperties;
    protected SoftBodySharedSettingsRef sharedSettingsRef;

    protected SoftPhysicsObject(PhysicsObjectType<? extends SoftPhysicsObject> type, Level level) {
        super(type, level);
        this.type = type;
        if (!(properties instanceof SoftPhysicsObjectProperties defaultProps)) {
            throw new IllegalArgumentException("SoftPhysicsObject requires SoftPhysicsObjectProperties");
        }
        this.softProperties = defaultProps;
    }

    @Override
    public PhysicsObjectType<? extends SoftPhysicsObject> getPhysicsObjectType() {
        return this.type;
    }

    @Override
    protected void addAdditionalSpawnData(FriendlyByteBuf buf) {}

    @Override
    protected void readAdditionalSpawnData(FriendlyByteBuf buf) {}

    protected abstract SoftBodySharedSettings buildSharedSettings();

    @SuppressWarnings("resource")
    public SoftBodySharedSettings getOrBuildSharedSettings() {
        if (this.sharedSettingsRef == null) {
            SoftBodySharedSettings settingsTarget = buildSharedSettings();
            if (settingsTarget != null) {
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

    protected void configureAdditionalSoftBodyCreationSettings(SoftBodyCreationSettings settings) {}

    @Override
    public void initializePhysics(VxPhysicsWorld physicsWorld) {
        if (physicsInitialized || isRemoved || level.isClientSide() || physicsWorld == null || !physicsWorld.isRunning()) return;
        physicsWorld.queueCommand(new AddSoftBodyCommand(this.physicsId));
    }

    @Override
    public void removeFromPhysics(VxPhysicsWorld physicsWorld) {
        if (bodyId == 0 || physicsWorld == null || !physicsWorld.isRunning()) return;
        RemoveSoftBodyCommand.queue(physicsWorld, this.physicsId, this.bodyId);
        if (this.sharedSettingsRef != null) {
            this.sharedSettingsRef.close();
            this.sharedSettingsRef = null;
        }
    }

    @Override
    public void updateStateFromPhysicsThread(long timestampNanos, @Nullable VxTransform transform, @Nullable Vec3 linearVelocity, @Nullable Vec3 angularVelocity, @Nullable float[] softBodyVertices, boolean isActive) {
        super.updateStateFromPhysicsThread(timestampNanos, transform, linearVelocity, angularVelocity, softBodyVertices, isActive);
        if (isActive && softBodyVertices != null) {
            this.lastSyncedVertexData = softBodyVertices;
        }
    }

    @Override
    @Nullable
    public float[] getLastSyncedVertexData() {
        return this.lastSyncedVertexData;
    }

    @Override
    public void onRightClickWithTool(ServerPlayer player) {
        if (player.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof PhysicsRemoverItem && player.level() instanceof ServerLevel sl) {
            VxObjectManager manager = VxPhysicsWorld.get(sl.dimension()).getObjectManager();
            if (manager != null) {
                manager.removeObject(this.physicsId, VxRemovalReason.DISCARD);
            }
        }
    }

    @Override
    public void gameTick(ServerLevel serverLevel) {}

    @Override
    public void physicsTick(VxPhysicsWorld physicsWorld) {}

    @Override
    public final void fixedGameTick(ServerLevel level) {
        if (ridingProxy != null) {
            VxMainClass.LOGGER.warn("Soft Bodies cannot be ridden.");
            ridingProxy.kill();
            ridingProxy = null;
        }
    }

    @Override
    public final void fixedPhysicsTick(VxPhysicsWorld physicsWorld) {}

    public abstract static class Renderer {
        public abstract void render(ClientSoftPhysicsObjectData data, PoseStack poseStack, MultiBufferSource bufferSource, float partialTicks, int packedLight);
    }
}