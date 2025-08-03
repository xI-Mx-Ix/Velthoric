package net.xmx.vortex.physics.object.riding;

import com.github.stephengold.joltjni.RVec3;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.xmx.vortex.init.registry.EntityRegistry;
import net.xmx.vortex.math.VxTransform;
import net.xmx.vortex.physics.object.physicsobject.IPhysicsObject;
import net.xmx.vortex.physics.object.physicsobject.client.ClientObjectDataManager;
import net.xmx.vortex.physics.object.physicsobject.client.interpolation.RenderData;
import net.xmx.vortex.physics.object.physicsobject.manager.VxObjectManager;
import net.xmx.vortex.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public class RidingProxyEntity extends Entity {

    private static final EntityDataAccessor<Optional<UUID>> PHYSICS_OBJECT_ID =
            SynchedEntityData.defineId(RidingProxyEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    @Environment(EnvType.CLIENT)
    private VxTransform lastInterpolatedTransform;

    @Nullable
    private transient UUID intendedPassengerUUID;

    public RidingProxyEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.blocksBuilding = false;
    }

    public RidingProxyEntity(Level level, IPhysicsObject physicsObject) {
        this(EntityRegistry.RIDING_PROXY.get(), level);
        this.setPhysicsObject(physicsObject);
        RVec3 pos = physicsObject.getCurrentTransform().getTranslation();
        this.setPos(pos.xx(), pos.yy(), pos.zz());
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(PHYSICS_OBJECT_ID, Optional.empty());
    }

    public void setPhysicsObject(IPhysicsObject physicsObject) {
        this.entityData.set(PHYSICS_OBJECT_ID, Optional.of(physicsObject.getPhysicsId()));
    }

    public Optional<UUID> getPhysicsObjectId() {
        return this.entityData.get(PHYSICS_OBJECT_ID);
    }

    public void setIntendedPassenger(ServerPlayer player) {
        this.intendedPassengerUUID = player.getUUID();
    }

    @Nullable
    public UUID getIntendedPassengerUUID() {
        return this.intendedPassengerUUID;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.isRemoved()) {
            return;
        }

        Optional<UUID> physicsIdOpt = getPhysicsObjectId();
        if (physicsIdOpt.isEmpty()) {
            return;
        }

        UUID physicsId = physicsIdOpt.get();

        if (level().isClientSide()) {
            clientTick(physicsId);
        } else {
            serverTick(physicsId);
        }
    }

    private void serverTick(UUID physicsId) {
        VxPhysicsWorld world = VxPhysicsWorld.get(level().dimension());
        if (world == null || !world.isRunning()) {
            this.kill();
            return;
        }

        VxObjectManager objectManager = world.getObjectManager();
        Optional<IPhysicsObject> physicsObjectOpt = objectManager.getObject(physicsId);

        if (physicsObjectOpt.isEmpty() || physicsObjectOpt.get().isRemoved()) {
            this.remove(RemovalReason.DISCARDED);
            return;
        }

        IPhysicsObject physicsObject = physicsObjectOpt.get();
        VxTransform transform = physicsObject.getCurrentTransform();
        RVec3 pos = transform.getTranslation();
        this.setPos(pos.xx(), pos.yy(), pos.zz());

        if (physicsObject.getRidingProxy() != this) {
            this.remove(RemovalReason.DISCARDED);
            return;
        }

        if (this.intendedPassengerUUID != null) {
            ServerPlayer player = ((ServerLevel)this.level()).getServer().getPlayerList().getPlayer(this.intendedPassengerUUID);

            if (player == null || player.getVehicle() != this) {
                if (this.tickCount > 2) {
                    this.remove(RemovalReason.DISCARDED);
                }
            }
        } else {
            if (this.tickCount > 20 && !this.hasControllingPassenger()) {
                this.remove(RemovalReason.DISCARDED);
            }
        }
    }

    @Environment(EnvType.CLIENT)
    private void clientTick(UUID physicsId) {
        RenderData renderData = ClientObjectDataManager.getInstance().getRenderData(physicsId, 0.0f);
        if (renderData == null) {
            if (!this.isRemoved()) {
                this.remove(RemovalReason.DISCARDED);
            }
            return;
        }

        this.lastInterpolatedTransform = renderData.transform;
        RVec3 pos = this.lastInterpolatedTransform.getTranslation();
        this.setPos(pos.xx(), pos.yy(), pos.zz());
    }

    @Environment(EnvType.CLIENT)
    public Optional<VxTransform> getInterpolatedTransform() {
        return Optional.ofNullable(lastInterpolatedTransform);
    }

    @Environment(EnvType.CLIENT)
    public Optional<VxTransform> getInterpolatedTransform(float partialTicks) {
        return getPhysicsObjectId()
                .flatMap(uuid -> Optional.ofNullable(ClientObjectDataManager.getInstance().getRenderData(uuid, partialTicks)))
                .map(renderData -> renderData.transform);
    }

    @Override
    protected void positionRider(@NotNull Entity passenger, @NotNull MoveFunction pCallback) {
        if (this.hasPassenger(passenger)) {
            double passengerY = this.getY() + this.getPassengersRidingOffset() + passenger.getMyRidingOffset();
            pCallback.accept(passenger, this.getX(), passengerY, this.getZ());
        }
    }

    @Override
    public double getPassengersRidingOffset() {
        return 0.0D;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(@NotNull LivingEntity livingEntity) {
        if (level() instanceof ServerLevel serverLevel) {
            VxPhysicsWorld world = VxPhysicsWorld.get(serverLevel.dimension());
            if (world != null) {
                return getPhysicsObjectId()
                        .flatMap(world.getObjectManager()::getObject)
                        .map(obj -> {
                            RVec3 pos = obj.getCurrentTransform().getTranslation();
                            return new Vec3(pos.xx(), pos.yy() + 1.0, pos.zz());
                        })
                        .orElse(super.getDismountLocationForPassenger(livingEntity));
            }
        }
        return super.getDismountLocationForPassenger(livingEntity);
    }

    @Override
    public void remove(@NotNull RemovalReason reason) {
        this.ejectPassengers();
        super.remove(reason);
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag compound) {
        if (compound.hasUUID("PhysicsObjectUUID")) {
            this.entityData.set(PHYSICS_OBJECT_ID, Optional.of(compound.getUUID("PhysicsObjectUUID")));
        }
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag compound) {
        getPhysicsObjectId().ifPresent(uuid -> compound.putUUID("PhysicsObjectUUID", uuid));
    }

    @Override
    public @NotNull Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}