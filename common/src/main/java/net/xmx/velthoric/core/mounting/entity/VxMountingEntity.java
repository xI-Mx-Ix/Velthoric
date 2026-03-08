/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.mounting.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.core.behavior.VxBehaviorManager;
import net.xmx.velthoric.core.behavior.VxBehaviors;
import net.xmx.velthoric.core.mounting.behavior.VxMountBehavior;
import net.xmx.velthoric.core.body.client.VxClientBodyManager;
import net.xmx.velthoric.core.body.VxBody;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Optional;
import java.util.UUID;

/**
 * An invisible "proxy" entity that acts as a bridge between the physical Minecraft player
 * and a physics-backed body in the Velthoric engine.
 * <p>
 * This entity is mounted by the player on the server, while its position is precisely
 * managed by the {@link VxMountBehavior} to match a specific seat on a {@link VxBody}.
 * This architecture allows players to "ride" physics objects without legacy vanilla
 * entity physics interfering with the sub-tick simulation.
 * <p>
 * <b>Key Responsibilities:</b>
 * <ul>
 *   <li><b>State Synchronization:</b> Syncs the target body ID and seat ID to clients via {@link SynchedEntityData}.</li>
 *   <li><b>Lifecycle Management:</b> Automatically discards itself if it has no riders or its target body disappears.</li>
 *   <li><b>Persistence:</b> Saves its link to the physics body in NBT to restore rider sessions after restarts.</li>
 *   <li><b>Physics Optimization:</b> Overrides almost all interaction and movement logic to prevent vanilla performance overhead.</li>
 * </ul>
 *
 * @author xI-Mx-Ix
 */
public class VxMountingEntity extends Entity {

    /**
     * Synced data accessor for the UUID of the physics body this entity is attached to.
     * This is used by the client to find the correct interpolation target.
     */
    private static final EntityDataAccessor<Optional<UUID>> PHYSICS_ID =
            SynchedEntityData.defineId(VxMountingEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    /**
     * Synced data accessor for the UUID of the specific seat on the body.
     * This allows the client to retrieve the correct local-space offset and orientation.
     */
    private static final EntityDataAccessor<Optional<UUID>> SEAT_ID =
            SynchedEntityData.defineId(VxMountingEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    /**
     * Standard constructor used by Minecraft's entity factory.
     * Configures the entity to be as lightweight as possible.
     *
     * @param entityType The registry type of this entity.
     * @param level      The world level it belongs to.
     */
    public VxMountingEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        // Disable vanilla entity gravity and collision logic.
        this.noPhysics = true;
        // Prevents the client from trying to "build" or interpolate this entity in ways that flicker.
        this.blocksBuilding = false;
        // Explicitly tells the engine there is no mass-based gravity applied here.
        this.setNoGravity(true);
    }

    /**
     * Main lifecycle loop called every server/client tick.
     * Manages cleanup and association restoration.
     */
    @Override
    public void tick() {
        // Base entity tick handles passenger updates and basic state.
        super.tick();

        // Server-side specific validation logic.
        if (!level().isClientSide) {

            // 1. Cleanup Check: If no one is riding this proxy anymore, it's garbage.
            if (getPassengers().isEmpty()) {
                discard();
                return;
            }

            // 2. Integrity Check: Ensure the physics body it point to still exists in the world.
            Optional<UUID> physicsIdOpt = getPhysicsId();
            if (physicsIdOpt.isPresent()) {
                VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(level().dimension());
                if (physicsWorld != null) {
                    // If the body is gone (e.g., deleted), we cannot maintain the mounting.
                    if (physicsWorld.getBodyManager().getVxBody(physicsIdOpt.get()) == null) {
                        this.discard();
                        return; // Exit early as the entity is now marked for removal.
                    }
                }
            }

            // 3. Session Restoration: Handle edge cases where the entity is loaded but not yet tracked.
            // This is primarily for server restarts or chunk re-entries.
            restoreMountingLinkIfNeeded();
        } else {
            // Client-side Snapping:
            // Precisely align the proxy entity with the target seat on the physics body every frame.
            // This ensures the player's world-space position is updated without network lag.
            getPhysicsId().ifPresent(physicsId -> {
                getSeatId().ifPresent(seatId -> {
                    VxClientBodyManager manager = VxClientBodyManager.getInstance();
                    VxBody body = manager.getVxBody(physicsId);
                    if (body != null) {
                        VxMountBehavior behavior = manager.getBehaviorManager().getBehavior(VxBehaviors.MOUNTABLE);
                        if (behavior != null) {
                            behavior.getSeat(physicsId, seatId).ifPresent(seat -> {
                                // 1. Get current body transform (interpolated).
                                var transform = body.getTransform();
                                Vector3f bodyPos = new Vector3f();
                                Quaternionf bodyRot = new Quaternionf();
                                transform.getTranslation(bodyPos);
                                transform.getRotation(bodyRot);

                                // 2. Calculate world-space seat position.
                                Vector3f seatOffset = new Vector3f(seat.getRiderOffset());
                                bodyRot.transform(seatOffset);

                                // 3. Snap proxy to seat position.
                                this.setPos(bodyPos.x + seatOffset.x, bodyPos.y + seatOffset.y, bodyPos.z + seatOffset.z);

                                // 4. Sync rotation.
                                float yaw = (float) Math.toDegrees(bodyRot.getEulerAnglesXYZ(new Vector3f()).y);
                                this.setYRot(yaw);
                                this.setYHeadRot(yaw);
                            });
                        }
                    }
                });
            });
        }
    }

    /**
     * Re-establishes the connection between a riding player and the {@link VxMountBehavior}.
     * This is necessary because players riding entities are saved by vanilla, but the
     * behavior's runtime tracking map is lost on server restart.
     */
    private void restoreMountingLinkIfNeeded() {
        if (this.getFirstPassenger() instanceof ServerPlayer player) {
            Optional<UUID> physicsIdOpt = getPhysicsId();
            Optional<UUID> seatIdOpt = getSeatId();

            // If we have all required IDs, notify the behavior to reclaim this session.
            if (physicsIdOpt.isPresent() && seatIdOpt.isPresent()) {
                VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(level().dimension());
                if (physicsWorld != null) {
                    VxBehaviorManager behaviorManager = physicsWorld.getBodyManager().getBehaviorManager();
                    VxMountBehavior behavior = behaviorManager.getBehavior(VxBehaviors.MOUNTABLE);

                    // If the behavior doesn't know about this player yet, restore the link.
                    if (behavior != null && !behavior.isMounting(player)) {
                        behavior.restoreMounting(player, physicsIdOpt.get(), seatIdOpt.get());
                    }
                }
            }
        }
    }

    // ================================================================================
    // Optimization Overrides: Stripping vanilla overhead
    // ================================================================================

    /**
     * Disables repulsion between entities. This proxy should never be moved by bumping into mobs/players.
     */
    @Override
    public boolean isPushable() {
        return false;
    }

    /**
     * Prevents this entity from pushing others.
     */
    @Override
    public void push(Entity entity) {
        // No-op - prevents the O(N^2) collision check logic from firing here.
    }

    /**
     * Disables fluid physics (water/lava flows).
     */
    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    /**
     * Blocks all vanilla movement calculations. The position is teleported by the physics engine.
     */
    @Override
    public void move(MoverType type, Vec3 pos) {
        // No-op - positions are set directly via setPos on the server tick.
    }

    /**
     * Prevents players from hitting, clicking, or interacting with the invisible proxy.
     */
    @Override
    public boolean isPickable() {
        return false;
    }

    /**
     * Disables broad-phase collision detection for this entity.
     */
    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    /**
     * Makes the proxy entity completely immune to all forms of damage.
     */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    /**
     * Prevents fire rendering and fire damage logic.
     */
    @Override
    public boolean fireImmune() {
        return true;
    }

    /**
     * Performance optimization: Tells the engine that this entity should never be "culled"
     * or Rendered in the traditional way, as it has no model.
     */
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return false;
    }

    /**
     * Prevents pistons from moving this entity.
     */
    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    /**
     * Registers the synced data fields for physics and seat ID.
     */
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(PHYSICS_ID, Optional.empty());
        builder.define(SEAT_ID, Optional.empty());
    }

    /**
     * Retrieves the unique identifier of the target physics body.
     *
     * @return An Optional containing the target's UUID.
     */
    public Optional<UUID> getPhysicsId() {
        return this.entityData.get(PHYSICS_ID);
    }

    /**
     * Retrieves the specific seat identifier on the target body.
     *
     * @return An Optional containing the seat UUID.
     */
    public Optional<UUID> getSeatId() {
        return this.entityData.get(SEAT_ID);
    }

    /**
     * Calculates the local position offset for the rider relative to the body's center.
     * <p>
     * <b>On Client:</b> Uses synced data to lookup the seat offset in the local mounting map.
     * <b>On Server:</b> Queries the {@link VxMountBehavior} directly for the player's seat.
     *
     * @return A vector representing the local-space rider offset.
     */
    public Vector3f getMountPositionOffset() {
        if (level().isClientSide()) {
            // Client: Lookup via synced IDs
            return getPhysicsId().flatMap(objId ->
                    getSeatId().flatMap(seatId -> {
                        VxMountBehavior behavior = VxClientBodyManager.getInstance().getBehaviorManager().getBehavior(VxBehaviors.MOUNTABLE);
                        return behavior != null ? behavior.getSeat(objId, seatId) : Optional.empty();
                    })
            ).map(seat -> new Vector3f(seat.getRiderOffset())).orElse(new Vector3f());
        } else {
            // Server: Lookup via player session in behavior
            if (this.getFirstPassenger() instanceof ServerPlayer player) {
                VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(level().dimension());
                if (physicsWorld != null) {
                    VxMountBehavior behavior = physicsWorld.getBodyManager().getBehaviorManager().getBehavior(VxBehaviors.MOUNTABLE);
                    return behavior != null ? behavior.getSeatForPlayer(player)
                            .map(seat -> new Vector3f(seat.getRiderOffset()))
                            .orElse(new Vector3f()) : new Vector3f();
                }
            }
            return new Vector3f();
        }
    }

    /**
     * Initializes the linkage between this entity and the physics body.
     * Should be called immediately after spawning the proxy.
     *
     * @param physicsId The target body UUID.
     * @param seatId    The target seat UUID.
     */
    public void setMountInfo(UUID physicsId, UUID seatId) {
        this.entityData.set(PHYSICS_ID, Optional.of(physicsId));
        this.entityData.set(SEAT_ID, Optional.of(seatId));
    }

    /**
     * Cleans up the mounting session when a passenger leaves.
     * If a player dismounts (via shift or command), we must notify the behavior system.
     *
     * @param passenger The entity leaving the proxy.
     */
    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);

        // Notify the server-side behavior to cleanup tracking and call 'onStopMounting' hooks.
        if (!this.level().isClientSide() && passenger instanceof ServerPlayer player) {
            VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(this.level().dimension());
            if (physicsWorld != null) {
                VxMountBehavior behavior = physicsWorld.getBodyManager().getBehaviorManager().getBehavior(VxBehaviors.MOUNTABLE);
                if (behavior != null) {
                    behavior.stopMounting((ServerLevel) this.level(), player);
                }
            }
        }
    }

    /**
     * Ensures the entity is never rendered.
     */
    @Override
    public boolean isInvisible() {
        return true;
    }

    /**
     * Internal: Serializes association data to disk.
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
     * Internal: Deserializes association data from disk.
     */
    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        getPhysicsId().ifPresent(uuid -> compound.putUUID("PhysicsId", uuid));
        getSeatId().ifPresent(uuid -> compound.putUUID("SeatId", uuid));
    }
}