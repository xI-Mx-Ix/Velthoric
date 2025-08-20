package net.xmx.vortex.physics.object.riding;// package net.xmx.vortex.physics.object.riding;

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

public class RidingProxyEntity extends Entity {

    private static final EntityDataAccessor<Optional<UUID>> PHYSICS_OBJECT_ID =
            SynchedEntityData.defineId(RidingProxyEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Vector3f> RIDE_POSITION_OFFSET =
            SynchedEntityData.defineId(RidingProxyEntity.class, EntityDataSerializers.VECTOR3);

    private float deltaRotation;

    public RidingProxyEntity(EntityType<?> entityType, Level level) {
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

    //@Override
    //public void onPassengerTurned(Entity entityToUpdate) {
    //     this.clampRotation(entityToUpdate);
    // }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        super.positionRider(passenger, callback);

        if (this.isControlledByLocalInstance() || !level().isClientSide()) {
            passenger.setYRot(passenger.getYRot() + this.deltaRotation);
            passenger.setYHeadRot(passenger.getYHeadRot() + this.deltaRotation);
            this.clampRotation(passenger);
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (!level().isClientSide) {
            float currentYaw = this.getYRot();
            this.deltaRotation = Mth.wrapDegrees(currentYaw - this.yRotO);
        }

        if (!level().isClientSide && getPassengers().isEmpty()) {
            discard();
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

    public void setFollowInfo(UUID physicsId, org.joml.Vector3f rideOffset) {
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