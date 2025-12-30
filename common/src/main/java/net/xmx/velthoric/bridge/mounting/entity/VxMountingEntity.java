/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.bridge.mounting.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.bridge.mounting.manager.VxMountingManager;
import net.xmx.velthoric.physics.world.VxClientPhysicsWorld;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.joml.Vector3f;

import java.util.Optional;
import java.util.UUID;

/**
 * An invisible entity that acts as a client-side bridge between a Minecraft player
 * and a mountable physics body. The player "mounts" this proxy entity. Its primary
 * role is to hold the physics body ID and the specific seat ID for the client to
 * perform efficient lookups.
 * <p>
 * This entity persists its association with the physics body to ensure riders remain
 * seated after server restarts or chunk reloads. It is stripped of almost all vanilla
 * physics, collision, and interaction logic to maximize performance and stability.
 *
 * @author xI-Mx-Ix
 */
public class VxMountingEntity extends Entity {

    private static final EntityDataAccessor<Optional<UUID>> PHYSICS_ID =
            SynchedEntityData.defineId(VxMountingEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> SEAT_ID =
            SynchedEntityData.defineId(VxMountingEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    /**
     * Constructs a new mounting entity.
     *
     * @param entityType The entity type.
     * @param level      The level the entity is in.
     */
    public VxMountingEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        // Prevents client-side interpolation issues when the physics body moves.
        this.blocksBuilding = false;
        // Disables gravity logic completely.
        this.setNoGravity(true);
    }

    @Override
    public void tick() {
        // Calls the base entity ticking logic (handling passengers, etc.) but skips
        // unnecessary logic if specific overrides are in place.
        super.tick();

        if (!level().isClientSide) {
            // If the proxy has no passengers on the server, it is no longer needed.
            if (getPassengers().isEmpty()) {
                discard();
                return;
            }

            // Check if the associated physics body still exists.
            // If the body was removed (e.g., destroyed), the seat proxy must be removed as well.
            Optional<UUID> physicsIdOpt = getPhysicsId();
            if (physicsIdOpt.isPresent()) {
                VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(level().dimension());
                if (physicsWorld != null) {
                    // Query the manager to see if the body with this ID exists.
                    // If getVxBody returns null, the body no longer exists.
                    if (physicsWorld.getBodyManager().getVxBody(physicsIdOpt.get()) == null) {
                        this.discard();
                        return;
                    }
                }
            }

            // Checks if this entity needs to re-establish its connection to the mounting manager.
            // This usually occurs after the entity has been loaded from disk (e.g., server restart).
            restoreMountingLinkIfNeeded();
        }
    }

    /**
     * Checks if this proxy entity has passengers and valid IDs but is not currently
     * tracked by the {@link VxMountingManager}. If so, it attempts to restore the session.
     */
    private void restoreMountingLinkIfNeeded() {
        if (this.getFirstPassenger() instanceof ServerPlayer player) {
            Optional<UUID> physicsIdOpt = getPhysicsId();
            Optional<UUID> seatIdOpt = getSeatId();

            if (physicsIdOpt.isPresent() && seatIdOpt.isPresent()) {
                VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(level().dimension());
                if (physicsWorld != null) {
                    VxMountingManager manager = physicsWorld.getMountingManager();

                    // If the manager does not track this player, ask the manager to restore the state.
                    if (!manager.isMounting(player)) {
                        manager.restoreMounting(player, physicsIdOpt.get(), seatIdOpt.get());
                    }
                }
            }
        }
    }

    // --- Interaction & Physics Optimization Overrides ---

    /**
     * Prevents the entity from being pushed by other entities (e.g., pistons or players).
     */
    @Override
    public boolean isPushable() {
        return false;
    }

    /**
     * Prevents the entity from pushing other entities.
     */
    @Override
    public void push(Entity entity) {
        // No-op
    }

    /**
     * Prevents the entity from being pushed by fluids (water/lava).
     */
    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    /**
     * Prevents any vanilla movement logic from applying. The position is strictly controlled
     * by the VxMountingManager.
     */
    @Override
    public void move(MoverType type, Vec3 pos) {
        // No-op: This entity should only be moved via setPos() by the manager.
    }

    /**
     * Prevents the entity from being targeted by raycasts (e.g., attacking or interacting).
     * The player should interact with the physics body's bounding box instead.
     */
    @Override
    public boolean isPickable() {
        return false;
    }

    /**
     * Defines the collision behavior. This entity ignores all collisions.
     */
    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    /**
     * Prevents the entity from taking damage.
     */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    /**
     * Prevents the entity from catching fire or displaying fire effects.
     */
    @Override
    public boolean fireImmune() {
        return true;
    }

    /**
     * Optimization: Tells the game engine that this entity should never be rendered via standard checks,
     * saving frustum culling calculations.
     */
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return false;
    }

    /**
     * Defines how this entity reacts to piston pushes. IGNORE prevents it from moving or breaking.
     */
    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(PHYSICS_ID, Optional.empty());
        this.entityData.define(SEAT_ID, Optional.empty());
    }

    /**
     * Gets the UUID of the physics body this proxy is associated with.
     *
     * @return An Optional containing the physics body's UUID.
     */
    public Optional<UUID> getPhysicsId() {
        return this.entityData.get(PHYSICS_ID);
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
     * On the client, this performs an efficient lookup using the synced body and seat IDs.
     * On the server, it queries the Mounting Manager to find the offset for the passenger.
     *
     * @return The rider's position offset vector. Returns a zero vector if not found.
     */
    public Vector3f getMountPositionOffset() {
        if (level().isClientSide()) {
            return getPhysicsId().flatMap(objId ->
                    getSeatId().flatMap(seatId ->
                            VxClientPhysicsWorld.getInstance().getMountingManager().getSeat(objId, seatId)
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
     * Sets the necessary data for the client to track the physics body and seat.
     * This is called on the server before the entity is spawned.
     *
     * @param physicsId The UUID of the physics body.
     * @param seatId    The UUID of the seat being occupied.
     */
    public void setMountInfo(UUID physicsId, UUID seatId) {
        this.entityData.set(PHYSICS_ID, Optional.of(physicsId));
        this.entityData.set(SEAT_ID, Optional.of(seatId));
    }

    /**
     * Handles passenger removal. If a player dismounts, it notifies the manager to cleanup the session.
     *
     * @param passenger The entity that is dismounting.
     */
    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);

        if (!this.level().isClientSide() && passenger instanceof ServerPlayer player) {
            VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(this.level().dimension());
            if (physicsWorld != null) {
                physicsWorld.getMountingManager().stopMounting(player);
            }
        }
    }

    @Override
    public boolean isInvisible() {
        return true;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    /**
     * Reads the physics body ID and seat ID from NBT to restore state after a restart.
     *
     * @param compound The NBT tag to read from.
     */
    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        if (compound.hasUUID("PhysicsId")) {
            this.entityData.set(PHYSICS_ID, Optional.of(compound.getUUID("PhysicsId")));
        }
        if (compound.hasUUID("SeatId")) {
            this.entityData.set(SEAT_ID, Optional.of(compound.getUUID("SeatId")));
        }
    }

    /**
     * Saves the physics body ID and seat ID to NBT to persist state across restarts.
     *
     * @param compound The NBT tag to write to.
     */
    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        getPhysicsId().ifPresent(uuid -> compound.putUUID("PhysicsId", uuid));
        getSeatId().ifPresent(uuid -> compound.putUUID("SeatId", uuid));
    }
}