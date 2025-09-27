/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.riding.manager;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.init.registry.EntityRegistry;
import net.xmx.velthoric.physics.object.type.VxBody;
import net.xmx.velthoric.physics.riding.VxRideable;
import net.xmx.velthoric.physics.riding.VxRidingProxyEntity;
import net.xmx.velthoric.physics.riding.input.VxRideInput;
import net.xmx.velthoric.physics.riding.seat.VxSeat;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all riding-related logic on the server side for a specific physics world.
 * This includes tracking which players are riding which objects, handling seat management,
 * processing player input, and synchronizing the position of riders.
 *
 * @author xI-Mx-Ix
 */
public class VxRidingManager {

    private final VxPhysicsWorld world;
    private final Object2ObjectMap<UUID, Map<String, VxSeat>> objectToSeatsMap = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<UUID, UUID> playerToObjectIdMap = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<UUID, Map<UUID, ServerPlayer>> objectToRidersMap = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<UUID, VxSeat> playerToSeatMap = new Object2ObjectOpenHashMap<>();

    public VxRidingManager(VxPhysicsWorld world) {
        this.world = world;
    }

    /**
     * Adds a seat to a physics object.
     *
     * @param objectId The UUID of the object.
     * @param seat     The seat to add.
     */
    public void addSeat(UUID objectId, VxSeat seat) {
        this.objectToSeatsMap.computeIfAbsent(objectId, k -> new ConcurrentHashMap<>()).put(seat.getName(), seat);
    }

    /**
     * Removes a seat from a physics object.
     *
     * @param objectId The UUID of the object.
     * @param seatName The name of the seat to remove.
     */
    public void removeSeat(UUID objectId, String seatName) {
        Map<String, VxSeat> seats = this.objectToSeatsMap.get(objectId);
        if (seats != null) {
            seats.remove(seatName);
            if (seats.isEmpty()) {
                this.objectToSeatsMap.remove(objectId);
            }
        }
    }

    /**
     * Handles a request from a client to start riding an object.
     * This method validates the request before initiating the ride.
     *
     * @param player   The player making the request.
     * @param objectId The UUID of the target object.
     * @param seatName The name of the target seat.
     */
    public void requestRiding(ServerPlayer player, UUID objectId, String seatName) {
        VxBody body = world.getObjectManager().getObject(objectId);
        if (!(body instanceof VxRideable rideable)) {
            VxMainClass.LOGGER.warn("Player {} requested to ride non-rideable object {}", player.getName().getString(), objectId);
            return;
        }

        Optional<VxSeat> seatOpt = getSeat(objectId, seatName);
        if (seatOpt.isEmpty()) {
            VxMainClass.LOGGER.warn("Player {} requested to ride non-existent seat '{}' on object {}", player.getName().getString(), seatName, objectId);
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
            VxMainClass.LOGGER.warn("Player {} is too far to ride object {} (distSq: {}, reach: {})", player.getName().getString(), objectId, distanceSq, reachDistance);
            return;
        }

        // If validation passes, start the riding process
        startRiding(player, rideable, seat);
    }

    /**
     * Gets a specific seat from an object by its name.
     *
     * @param objectId The UUID of the object.
     * @param seatName The name of the seat.
     * @return An Optional containing the seat if found.
     */
    public Optional<VxSeat> getSeat(UUID objectId, String seatName) {
        Map<String, VxSeat> seats = this.objectToSeatsMap.get(objectId);
        return seats != null ? Optional.ofNullable(seats.get(seatName)) : Optional.empty();
    }

    /**
     * Gets the seat a specific player is currently occupying.
     *
     * @param player The player.
     * @return An Optional containing the seat if the player is riding.
     */
    public Optional<VxSeat> getSeatForPlayer(ServerPlayer player) {
        return Optional.ofNullable(playerToSeatMap.get(player.getUUID()));
    }

    /**
     * Gets the rideable object a specific player is currently on.
     *
     * @param player The player.
     * @return An Optional containing the rideable object.
     */
    public Optional<VxRideable> getRideableForPlayer(ServerPlayer player) {
        UUID objectId = playerToObjectIdMap.get(player.getUUID());
        if (objectId == null) {
            return Optional.empty();
        }
        VxBody body = world.getObjectManager().getObject(objectId);
        if (body instanceof VxRideable) {
            return Optional.of((VxRideable) body);
        }
        return Optional.empty();
    }

    /**
     * Gets all seats associated with a physics object.
     *
     * @param objectId The UUID of the object.
     * @return A collection of all seats for the object.
     */
    public Collection<VxSeat> getSeats(UUID objectId) {
        Map<String, VxSeat> seats = this.objectToSeatsMap.get(objectId);
        return seats != null ? seats.values() : Collections.emptyList();
    }

    /**
     * Makes a player start riding a specific seat on a rideable object.
     *
     * @param player   The player to start riding.
     * @param rideable The rideable object.
     * @param seat     The seat to occupy.
     */
    public void startRiding(ServerPlayer player, VxRideable rideable, VxSeat seat) {
        if (player.level().isClientSide() || isRiding(player) || isSeatOccupied(rideable.getPhysicsId(), seat)) {
            return;
        }

        VxRidingProxyEntity proxy = new VxRidingProxyEntity(EntityRegistry.RIDING_PROXY.get(), player.level());
        Vector3f rideOffsetJoml = new Vector3f(seat.getRiderOffset());
        proxy.setFollowInfo(rideable.getPhysicsId(), rideOffsetJoml);

        var initialTransform = rideable.getTransform();
        var initialPos = initialTransform.getTranslation();
        var initialRot = initialTransform.getRotation();
        Quaternionf initialQuat = new Quaternionf(initialRot.getX(), initialRot.getY(), initialRot.getZ(), initialRot.getW());

        Vector3f worldOffset = new Vector3f(rideOffsetJoml);
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

        objectToRidersMap.computeIfAbsent(rideable.getPhysicsId(), k -> Maps.newHashMap()).put(player.getUUID(), player);
        playerToObjectIdMap.put(player.getUUID(), rideable.getPhysicsId());
        playerToSeatMap.put(player.getUUID(), seat);

        rideable.onStartRiding(player, seat);
    }

    /**
     * Makes a player stop riding their current object.
     *
     * @param player The player to stop riding.
     */
    public void stopRiding(ServerPlayer player) {
        if (!isRiding(player)) {
            return;
        }
        UUID playerUuid = player.getUUID();
        UUID objectId = playerToObjectIdMap.remove(playerUuid);
        playerToSeatMap.remove(playerUuid);

        if (objectId != null) {
            getRideableForPlayer(player).ifPresent(rideable -> rideable.onStopRiding(player));
            Map<UUID, ServerPlayer> riders = objectToRidersMap.get(objectId);
            if (riders != null) {
                riders.remove(playerUuid);
                if (riders.isEmpty()) {
                    objectToRidersMap.remove(objectId);
                }
            }
        }
        Entity vehicle = player.getVehicle();
        if (vehicle instanceof VxRidingProxyEntity) {
            player.stopRiding();
            vehicle.discard();
        }
    }

    /**
     * Called every server game tick to update the positions and states of all riders.
     */
    public void onGameTick() {
        List<ServerPlayer> playersToStopRiding = new ArrayList<>();
        Set<UUID> objectIds = Sets.newHashSet(objectToRidersMap.keySet());

        for (UUID objectId : objectIds) {
            Map<UUID, ServerPlayer> riders = objectToRidersMap.get(objectId);
            if (riders == null) continue;

            VxBody physObject = world.getObjectManager().getObject(objectId);
            if (physObject == null) {
                playersToStopRiding.addAll(riders.values());
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
                if (rider.isRemoved() || !rider.isPassenger() || !(rider.getVehicle() instanceof VxRidingProxyEntity)) {
                    playersToStopRiding.add(rider);
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
                        playersToStopRiding.add(rider);
                    }
                }
            }
        }

        for (ServerPlayer player : playersToStopRiding) {
            stopRiding(player);
        }
    }

    /**
     * Handles movement input from a riding player.
     *
     * @param player The player providing the input.
     * @param input  The input state.
     */
    public void handlePlayerInput(ServerPlayer player, VxRideInput input) {
        if (!isRiding(player)) {
            return;
        }

        getSeatForPlayer(player).ifPresent(seat -> {
            if (seat.isDriverSeat()) {
                getRideableForPlayer(player).ifPresent(rideable -> {
                    rideable.handleDriverInput(player, input);
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
        if (isRiding(player)) {
            stopRiding(player);
        }
    }

    public Map<UUID, Map<String, VxSeat>> getAllSeatsByObject() {
        return Collections.unmodifiableMap(this.objectToSeatsMap);
    }

    /**
     * Checks if a player is currently riding a physics object.
     *
     * @param player The player to check.
     * @return True if the player is riding.
     */
    public boolean isRiding(ServerPlayer player) {
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
            if (occupiedSeat != null && occupiedSeat.getName().equals(seat.getName())) {
                return true;
            }
        }
        return false;
    }
}