/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.tool;

import net.minecraft.server.level.ServerPlayer;
import net.xmx.velthoric.item.tool.config.VxToolConfig;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract base class for all tool logic.
 * <p>
 * Handles per-player configuration and state management.
 *
 * @author xI-Mx-Ix
 */
public abstract class VxToolMode {

    // Stores the configuration for each player
    private final Map<UUID, VxToolConfig> playerConfigs = new ConcurrentHashMap<>();
    
    // Stores the active state (e.g., is shooting) for each player
    private final Map<UUID, ActionState> playerStates = new ConcurrentHashMap<>();

    public enum ActionState {
        IDLE,
        PRIMARY_ACTIVE,
        SECONDARY_ACTIVE
    }

    /**
     * Called to define the configuration properties for this tool.
     *
     * @param config The config object to populate.
     */
    public abstract void registerProperties(VxToolConfig config);

    /**
     * Called every tick while the player is using the tool (holding the button).
     *
     * @param player The player using the tool.
     * @param config The player's current configuration.
     * @param state  The current action state (Primary/Secondary).
     */
    public abstract void onServerTick(ServerPlayer player, VxToolConfig config, ActionState state);

    /**
     * Gets the configuration for a specific player.
     * Creates a new one if it doesn't exist.
     *
     * @param playerUuid The player's UUID.
     * @return The tool configuration.
     */
    public VxToolConfig getConfig(UUID playerUuid) {
        return playerConfigs.computeIfAbsent(playerUuid, k -> {
            VxToolConfig config = new VxToolConfig();
            registerProperties(config);
            return config;
        });
    }

    /**
     * Updates the state of a player.
     *
     * @param player The player.
     * @param state  The new state.
     */
    public void setState(ServerPlayer player, ActionState state) {
        if (state == ActionState.IDLE) {
            playerStates.remove(player.getUUID());
        } else {
            playerStates.put(player.getUUID(), state);
        }
    }

    /**
     * Checks the player's current state.
     *
     * @param player The player.
     * @return The active state.
     */
    public ActionState getState(ServerPlayer player) {
        return playerStates.getOrDefault(player.getUUID(), ActionState.IDLE);
    }
}