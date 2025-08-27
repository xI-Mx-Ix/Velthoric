package net.xmx.velthoric.physics.riding;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.xmx.velthoric.init.registry.EntityRegistry;
import net.xmx.velthoric.physics.object.VxAbstractBody;
import net.xmx.velthoric.physics.riding.seat.Seat;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RidingManager {

    private final VxPhysicsWorld world;
    private final Object2ObjectMap<UUID, Map<String, Seat>> objectToSeatsMap = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<UUID, UUID> playerToObjectIdMap = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<UUID, Map<UUID, ServerPlayer>> objectToRidersMap = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<UUID, Seat> playerToSeatMap = new Object2ObjectOpenHashMap<>();

    public RidingManager(VxPhysicsWorld world) {
        this.world = world;
    }

    public void addSeat(UUID objectId, Seat.Properties properties) {
        Seat seat = new Seat(properties);
        this.objectToSeatsMap.computeIfAbsent(objectId, k -> new ConcurrentHashMap<>()).put(properties.seatName, seat);
    }

    public void removeSeat(UUID objectId, String seatName) {
        Map<String, Seat> seats = this.objectToSeatsMap.get(objectId);
        if (seats != null) {
            seats.remove(seatName);
            if (seats.isEmpty()) {
                this.objectToSeatsMap.remove(objectId);
            }
        }
    }

    public Optional<Seat> getSeat(UUID objectId, String seatName) {
        Map<String, Seat> seats = this.objectToSeatsMap.get(objectId);
        return seats != null ? Optional.ofNullable(seats.get(seatName)) : Optional.empty();
    }

    public Collection<Seat> getSeats(UUID objectId) {
        Map<String, Seat> seats = this.objectToSeatsMap.get(objectId);
        return seats != null ? seats.values() : Collections.emptyList();
    }

    public void lockSeat(UUID objectId, String seatName) {
        getSeat(objectId, seatName).ifPresent(Seat::lock);
    }

    public void unlockSeat(UUID objectId, String seatName) {
        getSeat(objectId, seatName).ifPresent(Seat::unlock);
    }

    public boolean isSeatLocked(UUID objectId, String seatName) {
        return getSeat(objectId, seatName).map(Seat::isLocked).orElse(true);
    }

    public Object2ObjectMap<UUID, Map<String, Seat>> getObjectToSeatsMap() {
        return this.objectToSeatsMap;
    }

    public void startRiding(ServerPlayer player, Rideable rideable, Seat seat) {
        if (player.level().isClientSide() || isRiding(player) || isSeatOccupied(rideable.getPhysicsId(), seat)) {
            return;
        }

        RidingProxyEntity proxy = new RidingProxyEntity(EntityRegistry.RIDING_PROXY.get(), player.level());
        Vector3f rideOffsetJoml = new Vector3f(seat.getRiderOffset());
        proxy.setFollowInfo(rideable.getPhysicsId(), rideOffsetJoml);

        var initialTransform = rideable.getGameTransform();
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
            Map<UUID, ServerPlayer> riders = objectToRidersMap.get(objectId);
            if (riders != null) {
                riders.remove(playerUuid);
                if (riders.isEmpty()) {
                    objectToRidersMap.remove(objectId);
                }
            }
            world.getObjectManager().getObject(objectId).ifPresent(object -> {
                if (object instanceof Rideable rideable) {
                    rideable.onStopRiding(player);
                }
            });
        }
        Entity vehicle = player.getVehicle();
        if (vehicle instanceof RidingProxyEntity) {
            player.stopRiding();
            vehicle.discard();
        }
    }

    public void ejectPassengerFromSeat(UUID objectId, String seatName) {
        Map<UUID, ServerPlayer> riders = objectToRidersMap.get(objectId);
        if (riders == null) return;

        Optional<ServerPlayer> playerToEject = riders.values().stream()
                .filter(player -> {
                    Seat seat = playerToSeatMap.get(player.getUUID());
                    return seat != null && seat.getName().equals(seatName);
                })
                .findFirst();

        playerToEject.ifPresent(this::stopRiding);
    }

    public void tick() {
        List<ServerPlayer> playersToStopRiding = new ArrayList<>();
        Set<UUID> objectIds = Sets.newHashSet(objectToRidersMap.keySet());

        for (UUID objectId : objectIds) {
            Map<UUID, ServerPlayer> riders = objectToRidersMap.get(objectId);
            if (riders == null) continue;

            Optional<VxAbstractBody> physObjectOpt = world.getObjectManager().getObject(objectId);
            if (physObjectOpt.isEmpty()) {
                playersToStopRiding.addAll(riders.values());
                continue;
            }

            VxAbstractBody physObject = physObjectOpt.get();
            var trans = physObject.getGameTransform();
            var pos = trans.getTranslation();
            var rot = trans.getRotation();
            Quaternionf jomlQuat = new Quaternionf(rot.getX(), rot.getY(), rot.getZ(), rot.getW());
            Vector3f eulerAngles = new Vector3f();
            jomlQuat.getEulerAnglesXYZ(eulerAngles);
            float yawDegrees = (float) Math.toDegrees(eulerAngles.y);

            for (ServerPlayer rider : Sets.newHashSet(riders.values())) {
                if (rider.isRemoved() || !rider.isPassenger() || !(rider.getVehicle() instanceof RidingProxyEntity)) {
                    playersToStopRiding.add(rider);
                } else {
                    Seat seat = this.playerToSeatMap.get(rider.getUUID());
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

    public void onPlayerDisconnect(ServerPlayer player) {
        if (isRiding(player)) {
            stopRiding(player);
        }
    }

    public boolean isRiding(ServerPlayer player) {
        return playerToObjectIdMap.containsKey(player.getUUID());
    }

    public boolean isSeatOccupied(UUID objectId, Seat seat) {
        Map<UUID, ServerPlayer> riders = objectToRidersMap.get(objectId);
        if (riders == null || riders.isEmpty()) {
            return false;
        }
        for (UUID riderUuid : riders.keySet()) {
            Seat occupiedSeat = playerToSeatMap.get(riderUuid);
            if (occupiedSeat != null && occupiedSeat.getName().equals(seat.getName())) {
                return true;
            }
        }
        return false;
    }
}