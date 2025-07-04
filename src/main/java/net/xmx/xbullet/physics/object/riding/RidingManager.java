package net.xmx.xbullet.physics.object.riding;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import net.xmx.xbullet.network.NetworkHandler;
import net.xmx.xbullet.physics.object.global.physicsobject.IPhysicsObject;
import net.xmx.xbullet.physics.object.riding.packet.MountPhysicsObjectPacket;
import net.xmx.xbullet.physics.object.riding.seat.Seat;
import net.xmx.xbullet.physics.world.PhysicsWorld;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class RidingManager {

    private static final ConcurrentMap<UUID, RidingInfo> RIDING_PLAYERS = new ConcurrentHashMap<>();

    private record RidingInfo(UUID physicsObjectId, String seatId) {}

    public static void serverTick(PhysicsWorld physicsWorld) {
        if (RIDING_PLAYERS.isEmpty()) return;

        RIDING_PLAYERS.forEach((playerUUID, ridingInfo) -> {
            ServerPlayer player = physicsWorld.getLevel().getServer().getPlayerList().getPlayer(playerUUID);
            Optional<IPhysicsObject> physicsObjectOpt = physicsWorld.getObjectManager().getObject(ridingInfo.physicsObjectId);

            if (player == null || physicsObjectOpt.isEmpty() || player.isRemoved()) {
                dismount(playerUUID, physicsWorld);
                return;
            }

            IPhysicsObject physicsObject = physicsObjectOpt.get();
            Seat seat = physicsObject.getSeat(ridingInfo.seatId);
            if (seat == null) {
                dismount(playerUUID, physicsWorld);
                return;
            }

            var targetTransform = seat.calculateWorldTransform();
            var pos = targetTransform.getTranslation();

            player.setPos(pos.xx(), pos.yy(), pos.zz());
        });
    }

    public static void mount(ServerPlayer player, IPhysicsObject physicsObject, Seat seat) {
        if (!(physicsObject instanceof IRideable)) return;
        if (isRiding(player)) {
            dismount(player.getUUID(), PhysicsWorld.get(player.level().dimension()));
        }

        UUID playerUUID = player.getUUID();
        RIDING_PLAYERS.put(playerUUID, new RidingInfo(physicsObject.getPhysicsId(), seat.getSeatId()));
        seat.setMountedPlayerUUID(playerUUID);

        player.setNoGravity(true);
        player.fallDistance = 0;

        player.startRiding(player, true);

        NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new MountPhysicsObjectPacket(player.getUUID(), physicsObject.getPhysicsId(), seat.getRelativeTransform()));
    }

    public static void dismount(UUID playerUUID, @Nullable PhysicsWorld world) {
        RidingInfo ridingInfo = RIDING_PLAYERS.remove(playerUUID);
        if (ridingInfo == null || world == null) return;

        world.getObjectManager().getObject(ridingInfo.physicsObjectId).ifPresent(obj -> {
            Seat seat = obj.getSeat(ridingInfo.seatId);
            if (seat != null) {
                seat.setMountedPlayerUUID(null);
            }
        });

        ServerPlayer player = world.getLevel().getServer().getPlayerList().getPlayer(playerUUID);
        if (player != null) {

            player.stopRiding();

            player.setNoGravity(false);

            Vec3 safePos = findSafeDismountLocation(world.getLevel(), player);
            player.teleportTo(safePos.x, safePos.y, safePos.z);

            NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                    new MountPhysicsObjectPacket(player.getUUID()));
        } else {

            NetworkHandler.CHANNEL.send(PacketDistributor.ALL.noArg(), new MountPhysicsObjectPacket(playerUUID));
        }
    }

    public static boolean isRiding(Player player) {
        return RIDING_PLAYERS.containsKey(player.getUUID());
    }

    public static void onPlayerDisconnect(Player player) {
        if (isRiding(player)) {
            dismount(player.getUUID(), PhysicsWorld.get(player.level().dimension()));
        }
    }

    private static Vec3 findSafeDismountLocation(Level level, Player player) {
        Vec3 safePos = DismountHelper.findSafeDismountLocation(player.getType(), level, player.blockPosition(), false);
        if (safePos != null) {
            return safePos;
        }

        return player.position().add(0, 0.5, 0);
    }
}