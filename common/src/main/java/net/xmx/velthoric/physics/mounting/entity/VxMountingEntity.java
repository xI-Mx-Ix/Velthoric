/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.mounting.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.xmx.velthoric.physics.mounting.manager.VxClientMountingManager;
import net.xmx.velthoric.physics.mounting.manager.VxMountingManager;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.joml.Vector3f;

import java.util.Optional;
import java.util.UUID;

/**
 * An invisible entity that acts as a client-side bridge between a Minecraft player
 * and a mountable physics object. The player "mounts" this proxy entity. Its primary
 * role is to hold the physics object ID and the specific seat ID for the client to
 * perform efficient lookups.
 *
 * @author xI-Mx-Ix
 */
public class VxMountingEntity extends Entity {

    private static final EntityDataAccessor<Optional<UUID>> PHYSICS_OBJECT_ID =
            SynchedEntityData.defineId(VxMountingEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> SEAT_ID =
            SynchedEntityData.defineId(VxMountingEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    public VxMountingEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            // If the proxy has no passengers on the server, it's obsolete and should be removed.
            if (getPassengers().isEmpty()) {
                discard();
            }
        }
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(PHYSICS_OBJECT_ID, Optional.empty());
        this.entityData.define(SEAT_ID, Optional.empty());
    }

    /**
     * Gets the UUID of the physics object this proxy is associated with.
     *
     * @return An Optional containing the physics object's UUID.
     */
    public Optional<UUID> getPhysicsObjectId() {
        return this.entityData.get(PHYSICS_OBJECT_ID);
    }

    /**
     * Gets the UUID of the seat the passenger of this proxy is occupying.
     *
     * @return An Optional containing the seat's UUID.
     */
    public Optional<UUID> getSeatId() {
        return this.entityData.get(SEAT_ID);
    }

    /**
     * Gets the local-space position offset for the mounted entity.
     * On the client, this performs an efficient lookup using the synced object and seat IDs.
     * On the server, it queries the Mounting Manager to find the offset for the passenger.
     *
     * @return The rider's position offset vector. Returns a zero vector if not found.
     */
    public Vector3f getMountPositionOffset() {
        if (level().isClientSide()) {
            return getPhysicsObjectId().flatMap(objId ->
                    getSeatId().flatMap(seatId ->
                            VxClientMountingManager.getInstance().getSeat(objId, seatId)
                    )
            ).map(seat -> new Vector3f(seat.getRiderOffset())).orElse(new Vector3f());
        } else {
            if (this.getFirstPassenger() instanceof ServerPlayer player) {
                VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(level().dimension());
                if (physicsWorld != null) {
                    VxMountingManager mountingManager = physicsWorld.getMountingManager();
                    return mountingManager.getSeatForPlayer(player)
                            .map(seat -> new Vector3f(seat.getRiderOffset()))
                            .orElse(new Vector3f());
                }
            }
            return new Vector3f();
        }
    }

    /**
     * Sets the necessary data for the client to track the physics object and seat.
     * This is called on the server before the entity is spawned.
     *
     * @param physicsId The UUID of the physics object.
     * @param seatId The UUID of the seat being occupied.
     */
    public void setMountInfo(UUID physicsId, UUID seatId) {
        this.entityData.set(PHYSICS_OBJECT_ID, Optional.of(physicsId));
        this.entityData.set(SEAT_ID, Optional.of(seatId));
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
    protected void readAdditionalSaveData(CompoundTag compound) {
        // This entity does not need to be saved.
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        // This entity does not need to be saved.
    }
}