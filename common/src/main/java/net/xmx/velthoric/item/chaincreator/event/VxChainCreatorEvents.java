/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.chaincreator.event;

import dev.architectury.event.events.common.PlayerEvent;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.velthoric.item.chaincreator.VxChainCreatorManager;

/**
 * Handles server-side events related to the Chain Creator functionality.
 *
 * @author xI-Mx-Ix
 */
public class VxChainCreatorEvents {

    /**
     * Registers all necessary server-side events.
     */
    public static void registerEvents() {
        PlayerEvent.PLAYER_QUIT.register(VxChainCreatorEvents::onPlayerQuit);
    }

    /**
     * Fired when a player quits the server. This cleans up any pending chain creation
     * data associated with that player to prevent memory leaks.
     * @param player The player who is quitting.
     */
    private static void onPlayerQuit(ServerPlayer player) {
        VxChainCreatorManager.INSTANCE.onPlayerQuit(player);
    }
}