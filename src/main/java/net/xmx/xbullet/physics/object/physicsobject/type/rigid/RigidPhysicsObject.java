package net.xmx.xbullet.physics.object.physicsobject.type.rigid;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.enumerate.EOverrideMassProperties;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.xmx.xbullet.item.PhysicsRemoverItem;
import net.xmx.xbullet.math.PhysicsTransform;
import net.xmx.xbullet.physics.object.physicsobject.AbstractPhysicsObject;
import net.xmx.xbullet.physics.object.physicsobject.EObjectType;
import net.xmx.xbullet.physics.object.physicsobject.manager.ObjectManager;
import net.xmx.xbullet.physics.object.physicsobject.properties.IPhysicsObjectProperties;
import net.xmx.xbullet.physics.object.physicsobject.type.rigid.client.ClientRigidPhysicsObjectData;
import net.xmx.xbullet.physics.object.physicsobject.type.rigid.pcmd.AddRigidBodyCommand;
import net.xmx.xbullet.physics.object.physicsobject.type.rigid.pcmd.RemoveRigidBodyCommand;
import net.xmx.xbullet.physics.object.physicsobject.type.rigid.properties.RigidPhysicsObjectProperties;
import net.xmx.xbullet.physics.world.PhysicsWorld;

import javax.annotation.Nullable;
import java.util.UUID;

public abstract class RigidPhysicsObject extends AbstractPhysicsObject {

    protected ShapeSettingsRef shapeSettingsRef;
    protected float mass;
    protected float friction;
    protected float restitution;
    protected float linearDamping;
    protected float angularDamping;
    protected float buoyancyFactor;
    protected float gravityFactor;
    protected EMotionType motionType;

    protected RigidPhysicsObject(UUID physicsId, Level level, String objectTypeIdentifier, PhysicsTransform initialTransform, IPhysicsObjectProperties properties) {
        super(physicsId, level, objectTypeIdentifier, initialTransform, properties);

        RigidPhysicsObjectProperties defaultProps = (RigidPhysicsObjectProperties) properties;
        this.mass = defaultProps.getMass();
        this.friction = defaultProps.getFriction();
        this.restitution = defaultProps.getRestitution();
        this.linearDamping = defaultProps.getLinearDamping();
        this.angularDamping = defaultProps.getAngularDamping();
        this.buoyancyFactor = defaultProps.getBuoyancyFactor();
        this.gravityFactor = defaultProps.getGravityFactor();
        this.motionType = defaultProps.getMotionType();
    }

    @Override
    protected final void addBodySpecificData(FriendlyByteBuf buf) {
        buf.writeFloat(this.mass);
        buf.writeFloat(this.friction);
        buf.writeFloat(this.restitution);
        buf.writeFloat(this.linearDamping);
        buf.writeFloat(this.angularDamping);
        buf.writeFloat(this.buoyancyFactor);
        buf.writeFloat(this.gravityFactor);
        buf.writeUtf(this.motionType.toString());
        buf.writeFloat(this.lastSyncedLinearVel.getX());
        buf.writeFloat(this.lastSyncedLinearVel.getY());
        buf.writeFloat(this.lastSyncedLinearVel.getZ());
        buf.writeFloat(this.lastSyncedAngularVel.getX());
        buf.writeFloat(this.lastSyncedAngularVel.getY());
        buf.writeFloat(this.lastSyncedAngularVel.getZ());

        addAdditionalData(buf);
    }

    @Override
    protected final void readBodySpecificData(FriendlyByteBuf buf) {
        this.mass = buf.readFloat();
        this.friction = buf.readFloat();
        this.restitution = buf.readFloat();
        this.linearDamping = buf.readFloat();
        this.angularDamping = buf.readFloat();
        this.buoyancyFactor = buf.readFloat();
        this.gravityFactor = buf.readFloat();
        try {
            this.motionType = EMotionType.valueOf(buf.readUtf());
        } catch (IllegalArgumentException e) {
            this.motionType = EMotionType.Dynamic;
        }
        this.lastSyncedLinearVel.set(buf.readFloat(), buf.readFloat(), buf.readFloat());
        this.lastSyncedAngularVel.set(buf.readFloat(), buf.readFloat(), buf.readFloat());

        readAdditionalData(buf);
    }

    protected void addAdditionalData(FriendlyByteBuf buf) {}

    protected void readAdditionalData(FriendlyByteBuf buf) {}

    @Override
    public EObjectType getPhysicsObjectType() {
        return EObjectType.RIGID_BODY;
    }

    protected abstract ShapeSettings buildShapeSettings();

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
        if (this.motionType != EMotionType.Static) {
            settings.setOverrideMassProperties(EOverrideMassProperties.CalculateMassAndInertia);
            MassProperties massProps = settings.getMassProperties();
            massProps.scaleToMass(this.mass);
            settings.setMassPropertiesOverride(massProps);
        }
        configureAdditionalRigidBodyCreationSettings(settings);
    }

    protected void configureAdditionalRigidBodyCreationSettings(BodyCreationSettings settings) {
    }

    @Override
    public void initializePhysics(PhysicsWorld physicsWorld) {
        if (physicsInitialized || isRemoved || level.isClientSide() || physicsWorld == null || !physicsWorld.isRunning()) {
            return;
        }
        physicsWorld.queueCommand(new AddRigidBodyCommand(this.physicsId, true));
    }

    @Override
    public void removeFromPhysics(PhysicsWorld physicsWorld) {
        if (bodyId == 0 || physicsWorld == null || !physicsWorld.isRunning()) {
            return;
        }
        RemoveRigidBodyCommand.queue(physicsWorld, this.physicsId, this.bodyId);
        if (this.shapeSettingsRef != null) {
            this.shapeSettingsRef.close();
            this.shapeSettingsRef = null;
        }
    }

    @Override
    public void gameTick(ServerLevel serverLevel) {
    }

    @Override
    public void physicsTick(PhysicsWorld physicsWorld) {
    }

    @Override
    public synchronized void updateStateFromPhysicsThread(long timestampNanos, @Nullable PhysicsTransform transform, @Nullable Vec3 linearVelocity, @Nullable Vec3 angularVelocity, @Nullable float[] softBodyVertices, boolean isActive) {
        if (this.isRemoved || level.isClientSide()) {
            return;
        }
        this.lastUpdateTimestampNanos = timestampNanos;
        this.isActive = isActive;
        if (isActive) {
            if (transform != null) {
                this.currentTransform.set(transform);
            }
            if (linearVelocity != null) {
                this.lastSyncedLinearVel.set(linearVelocity);
            }
            if (angularVelocity != null) {
                this.lastSyncedAngularVel.set(angularVelocity);
            }
        } else {
            this.lastSyncedLinearVel.loadZero();
            this.lastSyncedAngularVel.loadZero();
        }
    }

    @Override
    public void onRightClickWithTool(ServerPlayer player) {
        if (player.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof PhysicsRemoverItem && player.level() instanceof ServerLevel sl) {
            ObjectManager manager = PhysicsWorld.getObjectManager(sl.dimension());
            if (manager != null) {
                manager.deleteObject(this.physicsId);
            }
        }
    }

    public abstract static class Renderer {
        public abstract void render(ClientRigidPhysicsObjectData data, PoseStack poseStack, MultiBufferSource bufferSource, float partialTicks, int packedLight);
    }

    public float getFriction() {
        return friction;
    }

    public float getRestitution() {
        return restitution;
    }

    public float getLinearDamping() {
        return linearDamping;
    }

    public float getAngularDamping() {
        return angularDamping;
    }

    public float getBuoyancyFactor() {
        return buoyancyFactor;
    }

    public float getGravityFactor() {
        return gravityFactor;
    }

    public EMotionType getMotionType() {
        return motionType;
    }

    @Override
    public final void fixedGameTick(ServerLevel level) {
        if (this.ridingProxy == null) {
            return;
        }

        if (this.ridingProxy.isRemoved()) {
            this.ridingProxy = null;
        }
    }

    @Override
    public final void fixedPhysicsTick(PhysicsWorld physicsWorld) {
    }
}