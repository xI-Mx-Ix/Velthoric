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

public class VxRidingManager {

    private final VxPhysicsWorld world;
    private final Object2ObjectMap<UUID, Map<String, VxSeat>> objectToSeatsMap = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<UUID, UUID> playerToObjectIdMap = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<UUID, Map<UUID, ServerPlayer>> objectToRidersMap = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<UUID, VxSeat> playerToSeatMap = new Object2ObjectOpenHashMap<>();

    public VxRidingManager(VxPhysicsWorld world) {
        this.world = world;
    }

    public void addSeat(UUID objectId, VxSeat seat) {
        this.objectToSeatsMap.computeIfAbsent(objectId, k -> new ConcurrentHashMap<>()).put(seat.getName(), seat);
    }

    public void removeSeat(UUID objectId, String seatName) {
        Map<String, VxSeat> seats = this.objectToSeatsMap.get(objectId);
        if (seats != null) {
            seats.remove(seatName);
            if (seats.isEmpty()) {
                this.objectToSeatsMap.remove(objectId);
            }
        }
    }

    public Optional<VxSeat> getSeat(UUID objectId, String seatName) {
        Map<String, VxSeat> seats = this.objectToSeatsMap.get(objectId);
        return seats != null ? Optional.ofNullable(seats.get(seatName)) : Optional.empty();
    }

    public Optional<VxSeat> getSeatForPlayer(ServerPlayer player) {
        return Optional.ofNullable(playerToSeatMap.get(player.getUUID()));
    }

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

    public Collection<VxSeat> getSeats(UUID objectId) {
        Map<String, VxSeat> seats = this.objectToSeatsMap.get(objectId);
        return seats != null ? seats.values() : Collections.emptyList();
    }

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

    public void onPlayerDisconnect(ServerPlayer player) {
        if (isRiding(player)) {
            stopRiding(player);
        }
    }

    public Map<UUID, Map<String, VxSeat>> getAllSeatsByObject() {
        return Collections.unmodifiableMap(this.objectToSeatsMap);
    }

    public boolean isRiding(ServerPlayer player) {
        return playerToObjectIdMap.containsKey(player.getUUID());
    }

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