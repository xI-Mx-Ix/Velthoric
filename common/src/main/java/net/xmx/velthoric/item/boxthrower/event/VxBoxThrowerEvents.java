/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.boxthrower.event;

import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.velthoric.init.registry.ItemRegistry;
import net.xmx.velthoric.item.boxthrower.VxBoxThrowerManager;

public class VxBoxThrowerEvents {

    public static void registerEvents() {
        TickEvent.SERVER_POST.register(VxBoxThrowerEvents::onServerPostTick);
        PlayerEvent.PLAYER_QUIT.register(VxBoxThrowerEvents::onPlayerQuit);
    }

    private static void onServerPostTick(net.minecraft.server.MinecraftServer server) {
        var manager = VxBoxThrowerManager.getInstance();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean isHoldingBoxGun = player.getMainHandItem().is(ItemRegistry.BOX_THROWER.get())
                    || player.getOffhandItem().is(ItemRegistry.BOX_THROWER.get());
            if (isHoldingBoxGun) {
                if (manager.isShooting(player)) {
                    manager.serverTick(player);
                }
            } else {
                manager.stopShooting(player);
            }
        }
    }

    private static void onPlayerQuit(ServerPlayer player) {
        VxBoxThrowerManager.getInstance().stopShooting(player);
    }
}
