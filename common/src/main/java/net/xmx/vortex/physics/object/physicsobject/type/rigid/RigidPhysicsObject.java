package net.xmx.vortex.physics.object.physicsobject.type.rigid;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.enumerate.EOverrideMassProperties;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.xmx.vortex.item.PhysicsRemoverItem;
import net.xmx.vortex.physics.object.physicsobject.AbstractPhysicsObject;
import net.xmx.vortex.physics.object.physicsobject.PhysicsObjectType;
import net.xmx.vortex.physics.object.physicsobject.client.interpolation.RenderData;
import net.xmx.vortex.physics.object.physicsobject.manager.VxObjectManager;
import net.xmx.vortex.physics.object.physicsobject.manager.VxRemovalReason;
import net.xmx.vortex.physics.object.physicsobject.type.rigid.pcmd.AddRigidBodyCommand;
import net.xmx.vortex.physics.object.physicsobject.type.rigid.pcmd.RemoveRigidBodyCommand;
import net.xmx.vortex.physics.object.physicsobject.type.rigid.properties.RigidPhysicsObjectProperties;
import net.xmx.vortex.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.UUID;

public abstract class RigidPhysicsObject extends AbstractPhysicsObject {

    protected final PhysicsObjectType<? extends RigidPhysicsObject> type;
    protected ShapeSettingsRef shapeSettingsRef;
    protected float mass;
    protected float friction;
    protected float restitution;
    protected float linearDamping;
    protected float angularDamping;
    protected float gravityFactor;
    protected EMotionType motionType;
    protected float buoyancyFactor;

    protected RigidPhysicsObject(PhysicsObjectType<? extends RigidPhysicsObject> type, Level level) {
        super(type, level);
        this.type = type;
        RigidPhysicsObjectProperties defaultProps = (RigidPhysicsObjectProperties) properties;
        this.mass = defaultProps.getMass();
        this.friction = defaultProps.getFriction();
        this.restitution = defaultProps.getRestitution();
        this.linearDamping = defaultProps.getLinearDamping();
        this.angularDamping = defaultProps.getAngularDamping();
        this.gravityFactor = defaultProps.getGravityFactor();
        this.motionType = defaultProps.getMotionType();
        this.buoyancyFactor = defaultProps.getBuoyancyFactor();
    }

    @Override
    public PhysicsObjectType<? extends RigidPhysicsObject> getPhysicsObjectType() {
        return this.type;
    }

    @Override
    protected void addAdditionalSpawnData(FriendlyByteBuf buf) {}

    @Override
    protected void readAdditionalSpawnData(FriendlyByteBuf buf) {}

    protected abstract ShapeSettings buildShapeSettings();

    @SuppressWarnings("resource")
    public ShapeSettings getOrBuildShapeSettings() {
        if (this.shapeSettingsRef == null) {
            ShapeSettings settingsTarget = buildShapeSettings();
            if (settingsTarget != null) {
                this.shapeSettingsRef = settingsTarget.toRef();
            }
        }
        return (this.shapeSettingsRef != null) ? this.shapeSettingsRef.getPtr() : null;
    }

    public final void configureBodyCreationSettings(BodyCreationSettings settings) {
        settings.setFriction(this.friction);
        settings.setRestitution(this.restitution);
        settings.setLinearDamping(this.linearDamping);
        settings.setAngularDamping(this.angularDamping);
        settings.setGravityFactor(this.gravityFactor);
        settings.setLinearVelocity(this.lastSyncedLinearVel);
        settings.setAngularVelocity(this.lastSyncedAngularVel);
        settings.setEnhancedInternalEdgeRemoval(true);

        if (this.motionType != EMotionType.Static) {
            settings.setOverrideMassProperties(EOverrideMassProperties.CalculateMassAndInertia);
            MassProperties massProps = settings.getMassProperties();
            massProps.scaleToMass(this.mass);
            settings.setMassPropertiesOverride(massProps);
        }
        configureAdditionalRigidBodyCreationSettings(settings);
    }

    protected void configureAdditionalRigidBodyCreationSettings(BodyCreationSettings settings) {}

    @Override
    public void initializePhysics(VxPhysicsWorld physicsWorld) {
        if (physicsInitialized || isRemoved || level.isClientSide() || physicsWorld == null || !physicsWorld.isRunning()) return;
        physicsWorld.queueCommand(new AddRigidBodyCommand(this.physicsId));
    }

    @Override
    public void removeFromPhysics(VxPhysicsWorld physicsWorld) {
        if (bodyId == 0 || physicsWorld == null || !physicsWorld.isRunning()) return;
        RemoveRigidBodyCommand.queue(physicsWorld, this.physicsId, this.bodyId);
        if (this.shapeSettingsRef != null) {
            this.shapeSettingsRef.close();
            this.shapeSettingsRef = null;
        }
    }

    @Override
    public void gameTick(ServerLevel serverLevel) {}

    @Override
    public void physicsTick(VxPhysicsWorld physicsWorld) {}

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
    public final void fixedGameTick(ServerLevel level) {
        if (this.ridingProxy != null && this.ridingProxy.isRemoved()) {
            this.ridingProxy = null;
        }
    }

    @Override
    public final void fixedPhysicsTick(VxPhysicsWorld physicsWorld) {}

    public abstract static class Renderer {
        public abstract void render(UUID id, RenderData renderData, @Nullable ByteBuffer customData, PoseStack poseStack, MultiBufferSource bufferSource, float partialTicks, int packedLight);
    }

    public EMotionType getMotionType() {
        return this.motionType;
    }

    public float getBuoyancyFactor() {
        return this.buoyancyFactor;
    }
}