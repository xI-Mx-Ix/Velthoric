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
import net.xmx.velthoric.physics.object.type.VxBody;
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
    /** Maps a physics object's UUID to a map of its seats, where each seat is keyed by its own UUID. */
    private final Object2ObjectMap<UUID, Map<UUID, VxSeat>> objectToSeatsMap = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<UUID, UUID> playerToObjectIdMap = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<UUID, Map<UUID, ServerPlayer>> objectToRidersMap = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<UUID, VxSeat> playerToSeatMap = new Object2ObjectOpenHashMap<>();

    public VxMountingManager(VxPhysicsWorld world) {
        this.world = world;
    }

    /**
     * Adds a seat to a physics object, indexed by the seat's UUID.
     *
     * @param objectId The UUID of the object.
     * @param seat     The seat to add.
     */
    public void addSeat(UUID objectId, VxSeat seat) {
        this.objectToSeatsMap.computeIfAbsent(objectId, k -> new ConcurrentHashMap<>()).put(seat.getId(), seat);
    }

    /**
     * Removes a seat from a physics object using its UUID.
     *
     * @param objectId The UUID of the object.
     * @param seatId The UUID of the seat to remove.
     */
    public void removeSeat(UUID objectId, UUID seatId) {
        Map<UUID, VxSeat> seats = this.objectToSeatsMap.get(objectId);
        if (seats != null) {
            seats.remove(seatId);
            if (seats.isEmpty()) {
                this.objectToSeatsMap.remove(objectId);
            }
        }
    }

    /**
     * Handles a request from a client to start mounting an object.
     * This method validates the request before initiating the mount.
     *
     * @param player   The player making the request.
     * @param objectId The UUID of the target object.
     * @param seatId   The UUID of the target seat.
     */
    public void requestMounting(ServerPlayer player, UUID objectId, UUID seatId) {
        VxBody body = world.getObjectManager().getObject(objectId);
        if (!(body instanceof VxMountable mountable)) {
            VxMainClass.LOGGER.warn("Player {} requested to mount non-mountable object {}", player.getName().getString(), objectId);
            return;
        }

        Optional<VxSeat> seatOpt = getSeat(objectId, seatId);
        if (seatOpt.isEmpty()) {
            VxMainClass.LOGGER.warn("Player {} requested to mount non-existent seat '{}' on object {}", player.getName().getString(), seatId, objectId);
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
            VxMainClass.LOGGER.warn("Player {} is too far to mount object {} (distSq: {}, reach: {})", player.getName().getString(), objectId, distanceSq, reachDistance);
            return;
        }

        // If validation passes, start the mounting process
        startMounting(player, mountable, seat);
    }

    /**
     * Makes a player start mounting a specific seat on a mountable object.
     *
     * @param player    The player to start mounting.
     * @param mountable The mountable object.
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

        objectToRidersMap.computeIfAbsent(mountable.getPhysicsId(), k -> Maps.newHashMap()).put(player.getUUID(), player);
        playerToObjectIdMap.put(player.getUUID(), mountable.getPhysicsId());
        playerToSeatMap.put(player.getUUID(), seat);

        mountable.onStartMounting(player, seat);
    }

    /**
     * Makes a player stop mounting their current object.
     *
     * @param player The player to stop mounting.
     */
    public void stopMounting(ServerPlayer player) {
        if (!isMounting(player)) {
            return;
        }
        UUID playerUuid = player.getUUID();
        UUID objectId = playerToObjectIdMap.remove(playerUuid);
        playerToSeatMap.remove(playerUuid);

        if (objectId != null) {
            getMountableForPlayer(player).ifPresent(mountable -> mountable.onStopMounting(player));
            Map<UUID, ServerPlayer> riders = objectToRidersMap.get(objectId);
            if (riders != null) {
                riders.remove(playerUuid);
                if (riders.isEmpty()) {
                    objectToRidersMap.remove(objectId);
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
        Set<UUID> objectIds = Sets.newHashSet(objectToRidersMap.keySet());

        for (UUID objectId : objectIds) {
            Map<UUID, ServerPlayer> riders = objectToRidersMap.get(objectId);
            if (riders == null) continue;

            VxBody physObject = world.getObjectManager().getObject(objectId);
            if (physObject == null) {
                playersToStopMounting.addAll(riders.values());
                continue;
            }

            var trans = physObject.getTransform();
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
     * Checks if a player is currently mounting a physics object.
     *
     * @param player The player to check.
     * @return True if the player is mounting.
     */
    public boolean isMounting(ServerPlayer player) {
        return playerToObjectIdMap.containsKey(player.getUUID());
    }

    /**
     * Checks if a specific seat on an object is occupied.
     *
     * @param objectId The UUID of the object.
     * @param seat     The seat to check.
     * @return True if the seat is occupied.
     */
    public boolean isSeatOccupied(UUID objectId, VxSeat seat) {
        Map<UUID, ServerPlayer> riders = objectToRidersMap.get(objectId);
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
     * Gets a specific seat from an object by its UUID.
     *
     * @param objectId The UUID of the object.
     * @param seatId   The UUID of the seat.
     * @return An Optional containing the seat if found.
     */
    public Optional<VxSeat> getSeat(UUID objectId, UUID seatId) {
        Map<UUID, VxSeat> seats = this.objectToSeatsMap.get(objectId);
        return seats != null ? Optional.ofNullable(seats.get(seatId)) : Optional.empty();
    }

    /**
     * Gets all seats associated with a physics object.
     *
     * @param objectId The UUID of the object.
     * @return A collection of all seats for the object.
     */
    public Collection<VxSeat> getSeats(UUID objectId) {
        Map<UUID, VxSeat> seats = this.objectToSeatsMap.get(objectId);
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
     * Gets the mountable object a specific player is currently on.
     *
     * @param player The player.
     * @return An Optional containing the mountable object.
     */
    public Optional<VxMountable> getMountableForPlayer(ServerPlayer player) {
        UUID objectId = playerToObjectIdMap.get(player.getUUID());
        if (objectId == null) {
            return Optional.empty();
        }
        VxBody body = world.getObjectManager().getObject(objectId);
        if (body instanceof VxMountable) {
            return Optional.of((VxMountable) body);
        }
        return Optional.empty();
    }

    /**
     * Gets an unmodifiable view of all seats grouped by their parent object's UUID.
     *
     * @return A map of all seats.
     */
    public Map<UUID, Map<UUID, VxSeat>> getAllSeatsByObject() {
        return Collections.unmodifiableMap(this.objectToSeatsMap);
    }
}