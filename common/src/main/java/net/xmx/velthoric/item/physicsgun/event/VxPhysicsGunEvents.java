/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.physicsgun.event;

import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.velthoric.init.registry.ItemRegistry;
import net.xmx.velthoric.item.physicsgun.manager.VxPhysicsGunServerManager;

public class VxPhysicsGunEvents {

    public static void registerEvents() {
        TickEvent.SERVER_POST.register(VxPhysicsGunEvents::onServerPostTick);
        PlayerEvent.PLAYER_JOIN.register(VxPhysicsGunEvents::onPlayerJoin);
        PlayerEvent.PLAYER_QUIT.register(VxPhysicsGunEvents::onPlayerQuit);
    }

    private static void onServerPostTick(net.minecraft.server.MinecraftServer server) {
        var manager = VxPhysicsGunServerManager.getInstance();

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

                if (manager.isTryingToGrab(player) || manager.isGrabbing(player)) {
                    manager.stopGrabAttempt(player);
                }
            }
        }
    }

    private static void onPlayerJoin(ServerPlayer newPlayer) {

        VxPhysicsGunServerManager.getInstance().syncStateForNewPlayer(newPlayer);
    }

    private static void onPlayerQuit(ServerPlayer player) {

        VxPhysicsGunServerManager.getInstance().stopGrabAttempt(player);
    }
}
