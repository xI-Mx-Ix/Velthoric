/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.tool.event;

import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.xmx.velthoric.item.tool.VxToolMode;
import net.xmx.velthoric.item.tool.registry.VxToolRegistry;

/**
 * Central event handler for all registered tools.
 *
 * @author xI-Mx-Ix
 */
public class VxToolEvents {

    public static void registerEvents() {
        TickEvent.SERVER_POST.register(VxToolEvents::onServerTick);
        PlayerEvent.PLAYER_QUIT.register(VxToolEvents::onPlayerQuit);
    }

    private static void onServerTick(net.minecraft.server.MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Item heldItem = player.getMainHandItem().getItem();
            VxToolMode mode = VxToolRegistry.get(heldItem);

            if (mode != null) {
                VxToolMode.ActionState state = mode.getState(player);
                if (state != VxToolMode.ActionState.IDLE) {
                    // Execute the tool logic
                    mode.onServerTick(player, mode.getConfig(player.getUUID()), state);
                }
            } else {
                // If player switched item, ensure state is cleared in all tools
                // (Optimization: could track active tool per player instead of iterating all)
                // For now, we rely on the specific tool logic or clear on packet.
                // But to be safe, if they aren't holding a tool, they shouldn't be "shooting".
                // This part requires the tool mode to know if it should stop.
            }
        }
    }

    private static void onPlayerQuit(ServerPlayer player) {
        // Cleanup states
    }
}