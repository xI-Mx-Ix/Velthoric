/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.riding;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

import java.util.Optional;
import java.util.UUID;

/**
 * An invisible entity that acts as a bridge between a Minecraft player
 * and a physics-based object. The player "rides" this proxy entity. Its sole
 * purpose is to hold the physics object ID and seat offset for the client.
 *
 * @author xI-Mx-Ix
 */
public class VxRidingProxyEntity extends Entity {

    private static final EntityDataAccessor<Optional<UUID>> PHYSICS_OBJECT_ID =
            SynchedEntityData.defineId(VxRidingProxyEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Vector3f> RIDE_POSITION_OFFSET =
            SynchedEntityData.defineId(VxRidingProxyEntity.class, EntityDataSerializers.VECTOR3);

    private float deltaRotation;

    public VxRidingProxyEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
    }

    protected void clampRotation(Entity entityToUpdate) {
        entityToUpdate.setYBodyRot(this.getYRot());
        float f = Mth.wrapDegrees(entityToUpdate.getYRot() - this.getYRot());
        float g = Mth.clamp(f, -105.0F, 105.0F);
        entityToUpdate.yRotO += g - f;
        entityToUpdate.setYRot(entityToUpdate.getYRot() + g - f);
        entityToUpdate.setYHeadRot(entityToUpdate.getYRot());
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction callback) {
        super.positionRider(passenger, callback);
        if (!level().isClientSide()) {
            passenger.setYRot(passenger.getYRot() + this.deltaRotation);
            passenger.setYHeadRot(passenger.getYHeadRot() + this.deltaRotation);
            this.clampRotation(passenger);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            this.deltaRotation = Mth.wrapDegrees(this.getYRot() - this.yRotO);
            if (getPassengers().isEmpty()) {
                discard();
            }
        }
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(PHYSICS_OBJECT_ID, Optional.empty());
        this.entityData.define(RIDE_POSITION_OFFSET, new Vector3f());
    }

    public Optional<UUID> getPhysicsObjectId() {
        return this.entityData.get(PHYSICS_OBJECT_ID);
    }

    public Vector3f getRidePositionOffset() {
        return this.entityData.get(RIDE_POSITION_OFFSET);
    }

    public void setFollowInfo(UUID physicsId, Vector3f rideOffset) {
        this.entityData.set(PHYSICS_OBJECT_ID, Optional.of(physicsId));
        this.entityData.set(RIDE_POSITION_OFFSET, rideOffset);
    }

    @Override
    public boolean isInvisible() {
        return true;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {}
}