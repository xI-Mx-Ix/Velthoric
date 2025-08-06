package net.xmx.vortex.physics.object.riding;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.xmx.vortex.physics.object.physicsobject.IPhysicsObject;
import net.xmx.vortex.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Optional;
import java.util.UUID;

public class RidingProxyEntity extends Entity {

    private static final EntityDataAccessor<Optional<UUID>> PHYSICS_OBJECT_ID = SynchedEntityData.defineId(RidingProxyEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Vector3f> RIDE_POSITION_OFFSET = SynchedEntityData.defineId(RidingProxyEntity.class, EntityDataSerializers.VECTOR3);

    private static final EntityDataAccessor<Quaternionf> PHYSICS_OBJECT_ROTATION = SynchedEntityData.defineId(RidingProxyEntity.class, EntityDataSerializers.QUATERNION);

    public RidingProxyEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.blocksBuilding = true;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(PHYSICS_OBJECT_ID, Optional.empty());
        this.entityData.define(RIDE_POSITION_OFFSET, new Vector3f(0.0f, 0.0f, 0.0f));

        this.entityData.define(PHYSICS_OBJECT_ROTATION, new Quaternionf());
    }

    @Override
    public void tick() {
        super.tick();

        if (!level().isClientSide()) {
            getPhysicsObject().ifPresent(physicsObject -> {

                this.setPos(physicsObject.getCurrentTransform().getTranslation().x(),
                        physicsObject.getCurrentTransform().getTranslation().y(),
                        physicsObject.getCurrentTransform().getTranslation().z());

                var rotation = physicsObject.getCurrentTransform().getRotation();
                this.entityData.set(PHYSICS_OBJECT_ROTATION, new Quaternionf(rotation.getX(), rotation.getY(), rotation.getZ(), rotation.getW()));
            });
        }
    }

    public void setFollowInfo(UUID physicsId, com.github.stephengold.joltjni.Vec3 rideOffset) {
        this.entityData.set(PHYSICS_OBJECT_ID, Optional.of(physicsId));
        this.entityData.set(RIDE_POSITION_OFFSET, new Vector3f(rideOffset.getX(), rideOffset.getY(), rideOffset.getZ()));
    }

    @Nullable
    public UUID getPhysicsObjectId() {
        return this.entityData.get(PHYSICS_OBJECT_ID).orElse(null);
    }

    public Vec3 getRidePositionOffset() {
        Vector3f offset = this.entityData.get(RIDE_POSITION_OFFSET);
        return new Vec3(offset.x(), offset.y(), offset.z());
    }

    public Quaternionf getPhysicsObjectRotation() {
        return this.entityData.get(PHYSICS_OBJECT_ROTATION);
    }

    public Optional<IPhysicsObject> getPhysicsObject() {
        UUID id = getPhysicsObjectId();
        if (id == null || level().isClientSide) {
            return Optional.empty();
        }
        VxPhysicsWorld world = VxPhysicsWorld.get(level().dimension());
        if (world == null) {
            return Optional.empty();
        }
        return world.getObjectManager().getObject(id);
    }

    @Override
    public boolean isInvisible() {
        return true;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {}

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }
}