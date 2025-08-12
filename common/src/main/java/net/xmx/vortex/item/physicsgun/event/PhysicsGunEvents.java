package net.xmx.vortex.item.physicsgun.event;

import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.xmx.vortex.init.registry.ItemRegistry;
import net.xmx.vortex.item.physicsgun.GrabbedObjectInfo;
import net.xmx.vortex.item.physicsgun.manager.PhysicsGunClientManager;
import net.xmx.vortex.item.physicsgun.manager.PhysicsGunServerManager;
import net.xmx.vortex.item.physicsgun.packet.SyncAllPhysicsGunGrabsPacket;
import net.xmx.vortex.item.physicsgun.packet.SyncPlayersTryingToGrabPacket;
import net.xmx.vortex.network.NetworkHandler;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class PhysicsGunEvents {

    public static void registerEvents() {
        TickEvent.SERVER_POST.register(PhysicsGunEvents::onServerPostTick);
        PlayerEvent.PLAYER_JOIN.register(PhysicsGunEvents::onPlayerJoin);
        PlayerEvent.PLAYER_QUIT.register(PhysicsGunEvents::onPlayerQuit);
    }

    private static void onServerPostTick(net.minecraft.server.MinecraftServer server) {
        var manager = PhysicsGunServerManager.getInstance();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean isHoldingGun = player.getMainHandItem().is(ItemRegistry.PHYSICS_GUN.get())
                    || player.getOffhandItem().is(ItemRegistry.PHYSICS_GUN.get());

            if (isHoldingGun) {
                if (manager.isGrabbing(player)) {
                    manager.serverTick(player);
                } else if (manager.isTryingToGrab(player)) {
                    manager.startGrab(player);
                }
            } else {
                manager.stopGrabAttempt(player);
            }
        }
    }

    private static void onPlayerJoin(ServerPlayer newPlayer) {
        PhysicsGunServerManager manager = PhysicsGunServerManager.getInstance();

        Map<UUID, PhysicsGunClientManager.ClientGrabData> grabsForPacket = manager.getGrabbedObjects().entrySet().stream()
                .collect(Collectors.toConcurrentMap(
                        Map.Entry::getKey,
                        entry -> {
                            GrabbedObjectInfo info = entry.getValue();
                            Vec3 localHitPoint = new Vec3(
                                    info.grabPointLocal().getX(),
                                    info.grabPointLocal().getY(),
                                    info.grabPointLocal().getZ()
                            );
                            return new PhysicsGunClientManager.ClientGrabData(info.objectId(), localHitPoint);
                        }
                ));

        if (!grabsForPacket.isEmpty()) {
            NetworkHandler.sendToPlayer(new SyncAllPhysicsGunGrabsPacket(grabsForPacket), newPlayer);
        }

        if (!manager.getPlayersTryingToGrab().isEmpty()) {
            NetworkHandler.sendToPlayer(new SyncPlayersTryingToGrabPacket(manager.getPlayersTryingToGrab()), newPlayer);
        }
    }

    private static void onPlayerQuit(ServerPlayer player) {
        PhysicsGunServerManager.getInstance().stopGrabAttempt(player);
    }
}