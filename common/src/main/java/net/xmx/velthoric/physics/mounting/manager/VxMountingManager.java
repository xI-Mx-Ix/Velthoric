/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.mounting.manager;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.init.registry.EntityRegistry;
import net.xmx.velthoric.physics.mounting.VxMountable;
import net.xmx.velthoric.physics.mounting.entity.VxMountingEntity;
import net.xmx.velthoric.physics.mounting.input.VxMountInput;
import net.xmx.velthoric.physics.mounting.seat.VxSeat;
import net.xmx.velthoric.physics.body.type.VxBody;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all mounting-related logic on the server side for a specific physics world.
 * This includes tracking which players are mounting which objects, handling seat management,
 * processing player input, and synchronizing the position of mounted entities.
 * Seats are managed using their unique UUIDs as primary keys.
 *
 * @author xI-Mx-Ix
 */
public class VxMountingManager {

    private final VxPhysicsWorld world;
    /**
     *  Maps a physics body's UUID to a map of its seats, where each seat is keyed by its own UUID.
     */
    private final Object2ObjectMap<UUID, Map<UUID, VxSeat>> bodyToSeatsMap = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<UUID, UUID> playerToPhysicsIdMap = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<UUID, Map<UUID, ServerPlayer>> bodyToRidersMap = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<UUID, VxSeat> playerToSeatMap = new Object2ObjectOpenHashMap<>();

    public VxMountingManager(VxPhysicsWorld world) {
        this.world = world;
    }

    /**
     * Callback executed when a new physics body is added to the world.
     * If the body is mountable, this method registers all its defined seats.
     *
     * @param body The physics body that was added.
     */
    public void onBodyAdded(VxBody body) {
        if (body instanceof VxMountable mountable) {
            VxSeat.Builder seatBuilder = new VxSeat.Builder();
            mountable.defineSeats(seatBuilder);
            List<VxSeat> seats = seatBuilder.build();

            for (VxSeat seat : seats) {
                addSeat(body.getPhysicsId(), seat);
            }
        }
    }

    /**
     * Callback executed when a physics body is removed from the world.
     * This removes all seat data associated with the body.
     *
     * @param body The physics body that was removed.
     */
    public void onBodyRemoved(VxBody body) {
        if (body instanceof VxMountable) {
            this.bodyToSeatsMap.remove(body.getPhysicsId());
        }
    }

    /**
     * Adds a seat to a physics body, indexed by the seat's UUID.
     *
     * @param physicsId The UUID of the body.
     * @param seat     The seat to add.
     */
    public void addSeat(UUID physicsId, VxSeat seat) {
        this.bodyToSeatsMap.computeIfAbsent(physicsId, k -> new ConcurrentHashMap<>()).put(seat.getId(), seat);
    }

    /**
     * Removes a seat from a physics body using its UUID.
     *
     * @param physicsId The UUID of the body.
     * @param seatId The UUID of the seat to remove.
     */
    public void removeSeat(UUID physicsId, UUID seatId) {
        Map<UUID, VxSeat> seats = this.bodyToSeatsMap.get(physicsId);
        if (seats != null) {
            seats.remove(seatId);
            if (seats.isEmpty()) {
                this.bodyToSeatsMap.remove(physicsId);
            }
        }
    }

    /**
     * Handles a request from a client to start mounting a body.
     * This method validates the request before initiating the mount.
     *
     * @param player   The player making the request.
     * @param physicsId The UUID of the target body.
     * @param seatId   The UUID of the target seat.
     */
    public void requestMounting(ServerPlayer player, UUID physicsId, UUID seatId) {
        VxBody body = world.getBodyManager().getVxBody(physicsId);
        if (!(body instanceof VxMountable mountable)) {
            VxMainClass.LOGGER.warn("Player {} requested to mount non-mountable body {}", player.getName().getString(), physicsId);
            return;
        }

        Optional<VxSeat> seatOpt = getSeat(physicsId, seatId);
        if (seatOpt.isEmpty()) {
            VxMainClass.LOGGER.warn("Player {} requested to mount non-existent seat '{}' on body {}", player.getName().getString(), seatId, physicsId);
            return;
        }

        VxSeat seat = seatOpt.get();

        // Server-side validation using the player's actual reach distance.
        double reachDistance = player.isCreative() ? 5.0 : 4.5;

        double distanceSq = player.getEyePosition().distanceToSqr(
                body.getTransform().getTranslation().x(),
                body.getTransform().getTranslation().y(),
                body.getTransform().getTranslation().z()
        );

        if (distanceSq > (reachDistance * reachDistance)) {
            VxMainClass.LOGGER.warn("Player {} is too far to mount body {} (distSq: {}, reach: {})", player.getName().getString(), physicsId, distanceSq, reachDistance);
            return;
        }

        // If validation passes, start the mounting process
        startMounting(player, mountable, seat);
    }

    /**
     * Makes a player start mounting a specific seat on a mountable body.
     *
     * @param player    The player to start mounting.
     * @param mountable The mountable body.
     * @param seat      The seat to occupy.
     */
    public void startMounting(ServerPlayer player, VxMountable mountable, VxSeat seat) {
        if (player.level().isClientSide() || isMounting(player) || isSeatOccupied(mountable.getPhysicsId(), seat)) {
            return;
        }

        VxMountingEntity proxy = new VxMountingEntity(EntityRegistry.MOUNTING_ENTITY.get(), player.level());
        proxy.setMountInfo(mountable.getPhysicsId(), seat.getId());

        var initialTransform = mountable.getTransform();
        var initialPos = initialTransform.getTranslation();
        var initialRot = initialTransform.getRotation();
        Quaternionf initialQuat = new Quaternionf(initialRot.getX(), initialRot.getY(), initialRot.getZ(), initialRot.getW());

        Vector3f worldOffset = new Vector3f(seat.getRiderOffset());
        initialQuat.transform(worldOffset);

        double finalX = initialPos.x() + worldOffset.x();
        double finalY = initialPos.y() + worldOffset.y();
        double finalZ = initialPos.z() + worldOffset.z();

        Vector3f eulerAngles = new Vector3f();
        initialQuat.getEulerAnglesXYZ(eulerAngles);

        proxy.setPos(finalX, finalY, finalZ);
        proxy.setYRot((float) Math.toDegrees(eulerAngles.y));

        world.getLevel().addFreshEntity(proxy);
        player.startRiding(proxy, true);

        bodyToRidersMap.computeIfAbsent(mountable.getPhysicsId(), k -> Maps.newHashMap()).put(player.getUUID(), player);
        playerToPhysicsIdMap.put(player.getUUID(), mountable.getPhysicsId());
        playerToSeatMap.put(player.getUUID(), seat);

        mountable.onStartMounting(player, seat);
    }

    /**
     * Makes a player stop mounting their current body.
     *
     * @param player The player to stop mounting.
     */
    public void stopMounting(ServerPlayer player) {
        if (!isMounting(player)) {
            return;
        }
        UUID playerUuid = player.getUUID();
        UUID physicsId = playerToPhysicsIdMap.remove(playerUuid);
        playerToSeatMap.remove(playerUuid);

        if (physicsId != null) {
            getMountableForPlayer(player).ifPresent(mountable -> mountable.onStopMounting(player));
            Map<UUID, ServerPlayer> riders = bodyToRidersMap.get(physicsId);
            if (riders != null) {
                riders.remove(playerUuid);
                if (riders.isEmpty()) {
                    bodyToRidersMap.remove(physicsId);
                }
            }
        }
        Entity vehicle = player.getVehicle();
        if (vehicle instanceof VxMountingEntity) {
            player.stopRiding();
            vehicle.discard();
        }
    }

    /**
     * Called every server game tick to update the positions and states of all mounted entities.
     */
    public void onGameTick() {
        List<ServerPlayer> playersToStopMounting = new ArrayList<>();
        Set<UUID> objectIds = Sets.newHashSet(bodyToRidersMap.keySet());

        for (UUID physicsId : objectIds) {
            Map<UUID, ServerPlayer> riders = bodyToRidersMap.get(physicsId);
            if (riders == null) continue;

            VxBody physBody = world.getBodyManager().getVxBody(physicsId);
            if (physBody == null) {
                playersToStopMounting.addAll(riders.values());
                continue;
            }

            var trans = physBody.getTransform();
            var pos = trans.getTranslation();
            var rot = trans.getRotation();
            Quaternionf jomlQuat = new Quaternionf(rot.getX(), rot.getY(), rot.getZ(), rot.getW());
            Vector3f eulerAngles = new Vector3f();
            jomlQuat.getEulerAnglesXYZ(eulerAngles);
            float yawDegrees = (float) Math.toDegrees(eulerAngles.y);

            for (ServerPlayer rider : Sets.newHashSet(riders.values())) {
                if (rider.isRemoved() || !rider.isPassenger() || !(rider.getVehicle() instanceof VxMountingEntity)) {
                    playersToStopMounting.add(rider);
                } else {
                    VxSeat seat = this.playerToSeatMap.get(rider.getUUID());
                    if (seat != null) {
                        Entity vehicle = rider.getVehicle();
                        Vector3f rideOffset = new Vector3f(seat.getRiderOffset());
                        jomlQuat.transform(rideOffset);

                        double finalX = pos.x() + rideOffset.x();
                        double finalY = pos.y() + rideOffset.y();
                        double finalZ = pos.z() + rideOffset.z();

                        vehicle.setPos(finalX, finalY, finalZ);
                        vehicle.setYRot(yawDegrees);
                    } else {
                        playersToStopMounting.add(rider);
                    }
                }
            }
        }

        for (ServerPlayer player : playersToStopMounting) {
            stopMounting(player);
        }
    }

    /**
     * Handles movement input from a mounted player.
     *
     * @param player The player providing the input.
     * @param input  The input state.
     */
    public void handlePlayerInput(ServerPlayer player, VxMountInput input) {
        if (!isMounting(player)) {
            return;
        }

        getSeatForPlayer(player).ifPresent(seat -> {
            if (seat.isDriverSeat()) {
                getMountableForPlayer(player).ifPresent(mountable -> {
                    mountable.handleDriverInput(player, input);
                });
            }
        });
    }

    /**
     * Handles cleanup when a player disconnects from the server.
     *
     * @param player The player who disconnected.
     */
    public void onPlayerDisconnect(ServerPlayer player) {
        if (isMounting(player)) {
            stopMounting(player);
        }
    }

    /**
     * Checks if a player is currently mounting a physics body.
     *
     * @param player The player to check.
     * @return True if the player is mounting.
     */
    public boolean isMounting(ServerPlayer player) {
        return playerToPhysicsIdMap.containsKey(player.getUUID());
    }

    /**
     * Checks if a specific seat on a body is occupied.
     *
     * @param physicsId The UUID of the body.
     * @param seat     The seat to check.
     * @return True if the seat is occupied.
     */
    public boolean isSeatOccupied(UUID physicsId, VxSeat seat) {
        Map<UUID, ServerPlayer> riders = bodyToRidersMap.get(physicsId);
        if (riders == null || riders.isEmpty()) {
            return false;
        }
        for (UUID riderUuid : riders.keySet()) {
            VxSeat occupiedSeat = playerToSeatMap.get(riderUuid);
            if (occupiedSeat != null && occupiedSeat.getId().equals(seat.getId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets a specific seat from a body by its UUID.
     *
     * @param physicsId The UUID of the body.
     * @param seatId   The UUID of the seat.
     * @return An Optional containing the seat if found.
     */
    public Optional<VxSeat> getSeat(UUID physicsId, UUID seatId) {
        Map<UUID, VxSeat> seats = this.bodyToSeatsMap.get(physicsId);
        return seats != null ? Optional.ofNullable(seats.get(seatId)) : Optional.empty();
    }

    /**
     * Gets all seats associated with a physics body.
     *
     * @param physicsId The UUID of the body.
     * @return A collection of all seats for the body.
     */
    public Collection<VxSeat> getSeats(UUID physicsId) {
        Map<UUID, VxSeat> seats = this.bodyToSeatsMap.get(physicsId);
        return seats != null ? seats.values() : Collections.emptyList();
    }

    /**
     * Gets the seat a specific player is currently occupying.
     *
     * @param player The player.
     * @return An Optional containing the seat if the player is mounting.
     */
    public Optional<VxSeat> getSeatForPlayer(ServerPlayer player) {
        return Optional.ofNullable(playerToSeatMap.get(player.getUUID()));
    }

    /**
     * Gets the mountable body a specific player is currently on.
     *
     * @param player The player.
     * @return An Optional containing the mountable body.
     */
    public Optional<VxMountable> getMountableForPlayer(ServerPlayer player) {
        UUID physicsId = playerToPhysicsIdMap.get(player.getUUID());
        if (physicsId == null) {
            return Optional.empty();
        }
        VxBody body = world.getBodyManager().getVxBody(physicsId);
        if (body instanceof VxMountable) {
            return Optional.of((VxMountable) body);
        }
        return Optional.empty();
    }

    /**
     * Gets an unmodifiable view of all seats grouped by their parent body's UUID.
     *
     * @return A map of all seats.
     */
    public Map<UUID, Map<UUID, VxSeat>> getAllSeatsByBody() {
        return Collections.unmodifiableMap(this.bodyToSeatsMap);
    }
}