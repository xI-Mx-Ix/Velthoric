/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.magnetizer.event;

import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.velthoric.init.registry.ItemRegistry;
import net.xmx.velthoric.item.magnetizer.VxMagnetizerManager;

/**
 * Handles events related to the magnetizer.
 *
 * @author xI-Mx-Ix
 */
public class VxMagnetizerEvents {

    public static void registerEvents() {
        TickEvent.SERVER_POST.register(VxMagnetizerEvents::onServerPostTick);
        PlayerEvent.PLAYER_QUIT.register(VxMagnetizerEvents::onPlayerQuit);
    }

    private static void onServerPostTick(net.minecraft.server.MinecraftServer server) {
        var manager = VxMagnetizerManager.getInstance();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean isHoldingMagnetizer = player.getMainHandItem().is(ItemRegistry.MAGNETIZER.get())
                    || player.getOffhandItem().is(ItemRegistry.MAGNETIZER.get());

            if (isHoldingMagnetizer) {
                if (manager.isActing(player)) {
                    manager.serverTick(player);
                }
            } else {
                manager.stop(player);
            }
        }
    }

    private static void onPlayerQuit(ServerPlayer player) {
        VxMagnetizerManager.getInstance().stop(player);
    }
}