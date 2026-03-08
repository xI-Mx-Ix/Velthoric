/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.mounting.behavior;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.xmx.velthoric.core.behavior.VxBehavior;
import net.xmx.velthoric.core.behavior.VxBehaviorId;
import net.xmx.velthoric.core.behavior.VxBehaviorManager;
import net.xmx.velthoric.core.behavior.VxBehaviors;
import net.xmx.velthoric.core.body.server.VxServerBodyDataStore;
import net.xmx.velthoric.core.body.VxBody;
import net.xmx.velthoric.core.mounting.VxMountable;
import net.xmx.velthoric.core.mounting.entity.VxMountingEntity;
import net.xmx.velthoric.core.mounting.input.VxMountInput;
import net.xmx.velthoric.core.mounting.seat.VxSeat;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.init.registry.EntityRegistry;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A central, composition-based behavior that orchestrates all mounting-related logic
 * within the Velthoric physics environment.
 * <p>
 * This behavior serves as the successor to the legacy {@code VxMountingManager} and
 * {@code VxClientMountingManager}, unifying both server-side authoritative logic and
 * client-side interpolation/lookup into a single, cohesive module.
 * <p>
 * <b>Core Responsibilities:</b>
 * <ul>
 *   <li><b>Seat Lifecycle:</b> Manages the registration and lifecycle of {@link VxSeat} objects
 *       assigned to physics bodies via the {@link VxMountable} interface.</li>
 *   <li><b>Session Management:</b> Authoritatively tracks which players are riding which
 *       bodies/seats, handling both active play and session restoration after restarts.</li>
 *   <li><b>Position Synchronization:</b> Every server tick, translates the high-frequency
 *       physics body transform into vanilla Minecraft entity positions for the riding players.</li>
 *   <li><b>Input Pipeline:</b> Bridges the network input from riders (e.g., steering) to
 *       the respective {@link VxMountable} handler.</li>
 * </ul>
 * <p>
 * <b>Coordinate System:</b>
 * This behavior performs frequent transformations between <i>Local-Space</i> (relative to the
 * physics body center) and <i>World-Space</i> (absolute Minecraft coordinates).
 *
 * @author xI-Mx-Ix
 */
public class VxMountBehavior implements VxBehavior {

    /**
     * Maps a physics body's UUID to a map of its seats, where each seat is keyed by its own UUID.
     * This is used on both server and client for seat lookup.
     */
    private final Map<UUID, Map<UUID, VxSeat>> bodyToSeatsMap = new ConcurrentHashMap<>();

    // --- Server-Side Only State ---

    /**
     * Maps a player's UUID to the physics body they are currently associated with.
     */
    private final Object2ObjectMap<UUID, UUID> playerToPhysicsIdMap = new Object2ObjectOpenHashMap<>();

    /**
     * Groups all players currently riding a specific body.
     * Useful for bulk updates and cleanup when a body is removed.
     */
    private final Object2ObjectMap<UUID, Map<UUID, ServerPlayer>> bodyToRidersMap = new Object2ObjectOpenHashMap<>();

    /**
     * Directly maps a player's UUID to the {@link VxSeat} they are occupying.
     * Provides O(1) lookup during the ticking phase to avoid nested map scans.
     */
    private final Object2ObjectMap<UUID, VxSeat> playerToSeatMap = new Object2ObjectOpenHashMap<>();

    public VxMountBehavior() {
    }

    /**
     * Returns the unique identifier for this behavior.
     * Consumed by the {@link VxBehaviorManager} for bitmask allocation and dispatch.
     *
     * @return The MOUNTABLE behavior ID.
     */
    @Override
    public VxBehaviorId getId() {
        return VxBehaviors.MOUNTABLE;
    }

    // ================================================================================
    // Lifecycle Hooks
    // ================================================================================

    /**
     * Called when a physics body is registered and has the MOUNTABLE bit set.
     * Automatically scans the body for seat definitions.
     *
     * @param index The internal SoA index in the DataStore.
     * @param body  The body instance being attached to.
     */
    @Override
    public void onAttached(int index, VxBody body) {
        // We only care if the body implements the VxMountable interface.
        if (body instanceof VxMountable mountable) {
            // Use the builder-based seat definition pattern.
            VxSeat.Builder builder = new VxSeat.Builder();
            mountable.defineSeats(builder);

            // Register each seat into our global lookup map.
            for (VxSeat seat : builder.build()) {
                addSeat(body.getPhysicsId(), seat);
            }
        }
    }

    /**
     * Called when a physics body is removed from the world.
     * Purges all seat metadata associated with that ID.
     *
     * @param index The internal SoA index.
     * @param body  The body instance being detached.
     */
    @Override
    public void onDetached(int index, VxBody body) {
        removeSeatsForBody(body.getPhysicsId());
    }

    /**
     * The heart of the server-side mounting synchronization.
     * Translates physics body transforms into proxy entity positions for all riders.
     *
     * @param level The current server level.
     * @param store The global SoA data store.
     */
    @Override
    public void onServerTick(ServerLevel level, VxServerBodyDataStore store) {
        // Intermediate list to handle dismounts during iteration to avoid concurrent modifications.
        List<ServerPlayer> playersToStopMounting = new ArrayList<>();

        // Iterate through all bodies that have active riders.
        Set<UUID> objectIds = Sets.newHashSet(bodyToRidersMap.keySet());

        VxPhysicsWorld world = VxPhysicsWorld.get(level.dimension());
        if (world == null) return;

        for (UUID physicsId : objectIds) {
            Map<UUID, ServerPlayer> riders = bodyToRidersMap.get(physicsId);
            if (riders == null) continue;

            // Retrieve the latest physics transform from the body manager.
            VxBody physBody = world.getBodyManager().getVxBody(physicsId);
            if (physBody == null) continue;

            var trans = physBody.getTransform();
            var pos = trans.getTranslation();
            var rot = trans.getRotation();

            // Prepare rotation math: We need to rotate the local seat offset by the body's native rotation.
            // Jolt uses a Hamilton quaternion (x, y, z, w).
            Quaternionf jomlQuat = new Quaternionf(rot.getX(), rot.getY(), rot.getZ(), rot.getW());

            // Extract yaw for entity rotation (Minecraft entities primarily use Y-axis Euler rotation for facing).
            Vector3f eulerAngles = new Vector3f();
            jomlQuat.getEulerAnglesXYZ(eulerAngles);
            float yawDegrees = (float) Math.toDegrees(eulerAngles.y);

            // Update each rider associated with this specific physics body.
            for (ServerPlayer rider : Sets.newHashSet(riders.values())) {
                // 1. Validation: If the player disconnected, died, or left the proxy entity, mark for dismount.
                if (rider.isRemoved() || !rider.isPassenger() || !(rider.getVehicle() instanceof VxMountingEntity)) {
                    playersToStopMounting.add(rider);
                } else {
                    // 2. Transformation Logic: Mapping local-space seat to world-space entity.
                        // Transformation Logic: Mapping local-space seat to world-space entity.
                        VxSeat seat = this.playerToSeatMap.get(rider.getUUID());
                        if (seat != null) {
                            Entity vehicle = rider.getVehicle();

                            // Start with the static local seat offset defined in the body type.
                            Vector3f rideOffset = new Vector3f(seat.getRiderOffset());

                            // Rotate the local offset vector into world-space orientation using the body's native rotation.
                            jomlQuat.transform(rideOffset);

                            // Final World-Space Position = Body Center + Rotated Local Offset.
                            double finalX = pos.x() + rideOffset.x();
                            double finalY = pos.y() + rideOffset.y();
                            double finalZ = pos.z() + rideOffset.z();

                            // Update the proxy entity which the player is actually riding.
                            // By placing the proxy at the seat location, the player appears
                            // correctly in-seat on both server and client.
                            vehicle.setPos(finalX, finalY, finalZ);
                            vehicle.setYRot(yawDegrees);
                            vehicle.setYHeadRot(yawDegrees);

                            // Force a network update bit to ensure the client receives the position delta promptly.
                            vehicle.hurtMarked = true;
                        } else {
                            playersToStopMounting.add(rider);
                        }
                }
            }
        }

        // Batch process identified dismounts after the main iteration.
        for (ServerPlayer player : playersToStopMounting) {
            stopMounting(level, player);
        }
    }

    // ================================================================================
    // Static Data Management: Seat Registry
    // ================================================================================

    /**
     * Registers a seat for a specific physics body.
     *
     * @param physicsId The unique identifier of the physics body.
     * @param seat      The seat configuration data.
     */
    public void addSeat(UUID physicsId, VxSeat seat) {
        this.bodyToSeatsMap.computeIfAbsent(physicsId, k -> new ConcurrentHashMap<>()).put(seat.getId(), seat);
    }

    /**
     * Purges all documented seats for a specific body.
     * Use this when a body is being unloaded or deleted.
     *
     * @param physicsId The identifier of the body to clear.
     */
    public void removeSeatsForBody(UUID physicsId) {
        this.bodyToSeatsMap.remove(physicsId);
    }

    /**
     * Safely retrieves a seat definition from the registration map.
     *
     * @param physicsId The target body ID.
     * @param seatId    The target seat ID.
     * @return An Optional containing the seat, or empty if not found.
     */
    public Optional<VxSeat> getSeat(UUID physicsId, UUID seatId) {
        Map<UUID, VxSeat> seats = this.bodyToSeatsMap.get(physicsId);
        return seats != null ? Optional.ofNullable(seats.get(seatId)) : Optional.empty();
    }

    /**
     * Returns a collection of all seats registered to a specific body.
     *
     * @param physicsId The target body identifier.
     * @return A collection of seats, or an empty list if none are found.
     */
    public Collection<VxSeat> getSeats(UUID physicsId) {
        Map<UUID, VxSeat> seats = this.bodyToSeatsMap.get(physicsId);
        return seats != null ? seats.values() : Collections.emptyList();
    }

    // ================================================================================
    // Server-Side Mounting Logic
    // ================================================================================

    /**
     * Server-side entry point for a player requesting to mount a physics body.
     * <p>
     * Validates distance (reachability), body capability, and seat availability
     * before delegating to {@link #startMounting}.
     *
     * @param level     The server level context.
     * @param player    The player making the request.
     * @param physicsId The UUID of the target physics body.
     * @param seatId    The UUID of the specific seat on that body.
     */
    public void requestMounting(ServerLevel level, ServerPlayer player, UUID physicsId, UUID seatId) {
        // 1. World Presence Validation
        VxPhysicsWorld world = VxPhysicsWorld.get(level.dimension());
        if (world == null) return;

        // 2. Trait Validation: Ensure the body actually supports being ridden.
        VxBody body = world.getBodyManager().getVxBody(physicsId);
        if (!(body instanceof VxMountable mountable)) {
            VxMainClass.LOGGER.warn("Player {} requested to mount non-mountable body {}", player.getName().getString(), physicsId);
            return;
        }

        // 3. Seat Integrity Validation: Does the requested seat exist in our registry?
        Optional<VxSeat> seatOpt = getSeat(physicsId, seatId);
        if (seatOpt.isEmpty()) {
            VxMainClass.LOGGER.warn("Player {} requested to mount non-existent seat '{}' on body {}", player.getName().getString(), seatId, physicsId);
            return;
        }

        // 4. Reachability Calculation (Standard Minecraft interaction logic).
        VxSeat seat = seatOpt.get();
        double reachDistance = player.isCreative() ? 5.0 : 4.5;
        double distanceSq = player.getEyePosition().distanceToSqr(
                body.getTransform().getTranslation().x(),
                body.getTransform().getTranslation().y(),
                body.getTransform().getTranslation().z()
        );

        // Sanity check to prevent "reach hacks" or desync-based mounting from too far away.
        if (distanceSq > (reachDistance * reachDistance)) {
            VxMainClass.LOGGER.warn("Player {} is too far to mount body {} (distSq: {}, reach: {})", player.getName().getString(), physicsId, distanceSq, reachDistance);
            return;
        }

        // If all checks pass, initiate the actual mounting transaction.
        startMounting(level, player, mountable, seat);
    }

    /**
     * Formalizes the mounting transaction: assigns a seat, spawns the proxy, and marks as occupied.
     *
     * @param level     The server level.
     * @param player    The player.
     * @param mountable The mountable body trait.
     * @param seat      The seat configuration.
     */
    public void startMounting(ServerLevel level, ServerPlayer player, VxMountable mountable, VxSeat seat) {
        // Prevent overlapping sessions for the same player or seat.
        if (isMounting(player) || isSeatOccupied(mountable.getPhysicsId(), seat)) return;

        // 1. Finalize the Proxy Entity
        // This entity acts as the 'vehicle' for the player in the vanilla engine.
        VxMountingEntity proxy = new VxMountingEntity(EntityRegistry.MOUNTING_ENTITY.get(), level);
        proxy.setMountInfo(mountable.getPhysicsId(), seat.getId());

        // 2. Initial Transform Alignment
        // We calculate the initial position so the player doesn't "teleport" from 0,0,0 on the first frame.
        var initialTransform = mountable.getTransform();
        var initialPos = initialTransform.getTranslation();
        var initialRot = initialTransform.getRotation();
        Quaternionf initialQuat = new Quaternionf(initialRot.getX(), initialRot.getY(), initialRot.getZ(), initialRot.getW());

        // Apply seat offset rotated by body orientation.
        Vector3f worldOffset = new Vector3f(seat.getRiderOffset());
        initialQuat.transform(worldOffset);

        proxy.setPos(initialPos.x() + worldOffset.x(), initialPos.y() + worldOffset.y(), initialPos.z() + worldOffset.z());

        // Match base body rotation.
        Vector3f eulerAngles = new Vector3f();
        initialQuat.getEulerAnglesXYZ(eulerAngles);
        proxy.setYRot((float) Math.toDegrees(eulerAngles.y));

        // 3. Spawning and Mounting
        // 'true' for startRiding forces the mount even if the entities are technically far apart.
        level.addFreshEntity(proxy);
        player.startRiding(proxy, true);

        // 4. Internal Registration
        registerMountingInternal(player, mountable.getPhysicsId(), seat);

        // 5. Call the trait hook (e.g., for vehicles to start engines or update UI).
        mountable.onStartMounting(player, seat);
    }

    /**
     * Internal helper to atomically update all tracking maps for a mounting session.
     */
    private void registerMountingInternal(ServerPlayer player, UUID physicsId, VxSeat seat) {
        bodyToRidersMap.computeIfAbsent(physicsId, k -> Maps.newHashMap()).put(player.getUUID(), player);
        playerToPhysicsIdMap.put(player.getUUID(), physicsId);
        playerToSeatMap.put(player.getUUID(), seat);
    }

    /**
     * Restores a mapping between a player and a seat.
     * Used primarily when entities load from disk.
     */
    public void restoreMounting(ServerPlayer player, UUID physicsId, UUID seatId) {
        getSeat(physicsId, seatId).ifPresent(seat -> registerMountingInternal(player, physicsId, seat));
    }

    /**
     * Effectively terminates a mounting session.
     * Handles trait notification, tracking cleanup, and entity discarding.
     *
     * @param level  The server level context.
     * @param player The player to dismount.
     */
    public void stopMounting(ServerLevel level, ServerPlayer player) {
        if (!isMounting(player)) return;

        // 1. Notify the object trait so it can handle dismount effects (like stopping engines).
        getMountableForPlayer(level, player).ifPresent(mountable -> mountable.onStopMounting(player));

        // 2. Cleanup session tracking state maps.
        UUID playerUuid = player.getUUID();
        UUID physicsId = playerToPhysicsIdMap.remove(playerUuid);
        playerToSeatMap.remove(playerUuid);

        if (physicsId != null) {
            Map<UUID, ServerPlayer> riders = bodyToRidersMap.get(physicsId);
            if (riders != null) {
                riders.remove(playerUuid);
                // Purge empty rider maps to keep memory clean.
                if (riders.isEmpty()) bodyToRidersMap.remove(physicsId);
            }
        }

        // 3. Coordinate with vanilla Entity system dismount.
        Entity vehicle = player.getVehicle();
        if (vehicle instanceof VxMountingEntity) {
            player.stopRiding();
            // Discard the no-longer-needed proxy entity.
            vehicle.discard();
        }
    }

    /**
     * Forwards player movement input (W/A/S/D/Space/Shift) to the target mountable.
     * Input is only processed if the player is in a designated driver seat.
     *
     * @param level  The server level.
     * @param player The player sending input.
     * @param input  The input state packet.
     */
    public void handlePlayerInput(ServerLevel level, ServerPlayer player, VxMountInput input) {
        if (!isMounting(player)) return;

        getSeatForPlayer(player).ifPresent(seat -> {
            // Only designated drivers should influence the body's physical movement.
            if (seat.isDriverSeat()) {
                getMountableForPlayer(level, player).ifPresent(mountable -> mountable.handleDriverInput(player, input));
            }
        });
    }

    /**
     * Checks if a particular player is currently active in a mounting session.
     *
     * @param player The target player.
     * @return True if they are mounting a physics body.
     */
    public boolean isMounting(ServerPlayer player) {
        return playerToPhysicsIdMap.containsKey(player.getUUID());
    }

    /**
     * Validates whether a specific seat on a body is already occupied by someone else.
     *
     * @param physicsId The body ID.
     * @param seat      The seat to check.
     * @return True if the seat is occupied.
     */
    public boolean isSeatOccupied(UUID physicsId, VxSeat seat) {
        Map<UUID, ServerPlayer> riders = bodyToRidersMap.get(physicsId);
        if (riders == null || riders.isEmpty()) return false;

        for (UUID riderUuid : riders.keySet()) {
            VxSeat occupiedSeat = playerToSeatMap.get(riderUuid);
            // Match based on unique seat UUID.
            if (occupiedSeat != null && occupiedSeat.getId().equals(seat.getId())) return true;
        }
        return false;
    }

    /**
     * Specialized lookup to retrieve the current seat assigned to a player.
     */
    public Optional<VxSeat> getSeatForPlayer(ServerPlayer player) {
        return Optional.ofNullable(playerToSeatMap.get(player.getUUID()));
    }

    /**
     * Resolves the {@link VxMountable} interface for a sitting player.
     * Performs a cascading lookup: Player -> Body UUID -> VxBody -> VxMountable.
     *
     * @param level  The current server level.
     * @param player The rider.
     * @return Optional containing the mountable body trait.
     */
    public Optional<VxMountable> getMountableForPlayer(ServerLevel level, ServerPlayer player) {
        UUID physicsId = playerToPhysicsIdMap.get(player.getUUID());
        if (physicsId == null) return Optional.empty();

        VxPhysicsWorld world = VxPhysicsWorld.get(level.dimension());
        if (world == null) return Optional.empty();

        VxBody body = world.getBodyManager().getVxBody(physicsId);
        if (body instanceof VxMountable mountable) return Optional.of(mountable);
        return Optional.empty();
    }
}