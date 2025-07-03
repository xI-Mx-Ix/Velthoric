package net.xmx.xbullet.physics.object.rigidphysicsobject;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.enumerate.EOverrideMassProperties;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.xmx.xbullet.item.PhysicsRemoverItem;
import net.xmx.xbullet.math.PhysicsTransform;
import net.xmx.xbullet.physics.world.PhysicsWorld;
import net.xmx.xbullet.physics.object.global.physicsobject.AbstractPhysicsObject;
import net.xmx.xbullet.physics.object.global.physicsobject.EObjectType;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.PhysicsObjectManager;
import net.xmx.xbullet.physics.object.global.physicsobject.properties.IPhysicsObjectProperties;
import net.xmx.xbullet.physics.object.rigidphysicsobject.client.ClientRigidPhysicsObjectData;
import net.xmx.xbullet.physics.object.rigidphysicsobject.pcmd.AddRigidBodyCommand;
import net.xmx.xbullet.physics.object.rigidphysicsobject.pcmd.RemoveRigidBodyCommand;
import net.xmx.xbullet.physics.object.rigidphysicsobject.properties.RigidPhysicsObjectProperties;

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

    protected RigidPhysicsObject(UUID physicsId, Level level, String objectTypeIdentifier, PhysicsTransform initialTransform, IPhysicsObjectProperties properties, @Nullable CompoundTag initialNbt) {
        super(physicsId, level, objectTypeIdentifier, initialTransform, properties, initialNbt);

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

    public void configureBodyCreationSettings(BodyCreationSettings settings) {
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
    }

    @Override
    public void initializePhysics(PhysicsWorld physicsWorld) {
        if (physicsInitialized || isRemoved || level.isClientSide() || physicsWorld == null || !physicsWorld.isRunning()) return;
        AddRigidBodyCommand.queue(physicsWorld, this, true);
    }

    @Override
    public void removeFromPhysics(PhysicsWorld physicsWorld) {
        if (bodyId == 0 || physicsWorld == null || !physicsWorld.isRunning()) return;
        RemoveRigidBodyCommand.queue(physicsWorld, this.physicsId, this.bodyId);
        if (this.shapeSettingsRef != null) {
            this.shapeSettingsRef.close();
            this.shapeSettingsRef = null;
        }
    }

    @Override
    public void serverTick(PhysicsWorld physicsWorld) {
    }

    @Override
    public synchronized void updateStateFromPhysicsThread(long timestampNanos, @Nullable PhysicsTransform transform, @Nullable Vec3 linearVelocity, @Nullable Vec3 angularVelocity, @Nullable float[] softBodyVertices, boolean isActive) {
        if (this.isRemoved || level.isClientSide()) return;

        this.lastUpdateTimestampNanos = timestampNanos;
        this.isActive = isActive;

        if (isActive) {
            if (transform != null) this.currentTransform.set(transform);
            if (linearVelocity != null) this.lastSyncedLinearVel.set(linearVelocity);
            if (angularVelocity != null) this.lastSyncedAngularVel.set(angularVelocity);
        } else {
            this.lastSyncedLinearVel.loadZero();
            this.lastSyncedAngularVel.loadZero();
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("mass", this.mass);
        tag.putFloat("friction", this.friction);
        tag.putFloat("restitution", this.restitution);
        tag.putFloat("linearDamping", this.linearDamping);
        tag.putFloat("angularDamping", this.angularDamping);
        tag.putFloat("buoyancyFactor", this.buoyancyFactor);
        tag.putFloat("gravityFactor", this.gravityFactor);
        tag.putString("motionType", this.motionType.toString());

        tag.put("linVel", newFloatList(this.lastSyncedLinearVel.getX(), this.lastSyncedLinearVel.getY(), this.lastSyncedLinearVel.getZ()));
        tag.put("angVel", newFloatList(this.lastSyncedAngularVel.getX(), this.lastSyncedAngularVel.getY(), this.lastSyncedAngularVel.getZ()));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.mass = tag.contains("mass") ? tag.getFloat("mass") : this.mass;
        this.friction = tag.contains("friction") ? tag.getFloat("friction") : this.friction;
        this.restitution = tag.contains("restitution") ? tag.getFloat("restitution") : this.restitution;
        this.linearDamping = tag.contains("linearDamping") ? tag.getFloat("linearDamping") : this.linearDamping;
        this.angularDamping = tag.contains("angularDamping") ? tag.getFloat("angularDamping") : this.angularDamping;
        this.buoyancyFactor = tag.contains("buoyancyFactor") ? tag.getFloat("buoyancyFactor") : this.buoyancyFactor;
        this.gravityFactor = tag.contains("gravityFactor") ? tag.getFloat("gravityFactor") : this.gravityFactor;

        if (tag.contains("motionType", StringTag.TAG_STRING)) {
            try {
                this.motionType = EMotionType.valueOf(tag.getString("motionType"));
            } catch (IllegalArgumentException e) {
                this.motionType = EMotionType.Dynamic;
            }
        }

        if (tag.contains("linVel", 9)) {
            ListTag list = tag.getList("linVel", 5);
            if (list.size() == 3) this.lastSyncedLinearVel.set(list.getFloat(0), list.getFloat(1), list.getFloat(2));
        }
        if (tag.contains("angVel", 9)) {
            ListTag list = tag.getList("angVel", 5);
            if (list.size() == 3) this.lastSyncedAngularVel.set(list.getFloat(0), list.getFloat(1), list.getFloat(2));
        }
    }

    @Override
    public void onRightClickWithTool(Player player) {
        if (player.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof PhysicsRemoverItem && player.level() instanceof ServerLevel sl) {
            PhysicsObjectManager manager = PhysicsWorld.getObjectManager(sl.dimension());
            manager.removeObject(this.physicsId, true);
        }
    }

    public abstract static class Renderer {
        public abstract void render(ClientRigidPhysicsObjectData data, PoseStack poseStack, MultiBufferSource bufferSource, float partialTicks, int packedLight);
    }

    public EMotionType getMotionType() {
        return motionType;
    }

    public float getMass() {
        return mass;
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
}