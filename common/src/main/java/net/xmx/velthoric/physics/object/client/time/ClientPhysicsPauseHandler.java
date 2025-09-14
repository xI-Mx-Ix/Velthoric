/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.client.time;

import dev.architectury.event.events.client.ClientTickEvent;
import net.minecraft.client.Minecraft;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

/**
 * Handles the pausing and resuming of the client-side physics simulation.
 * It listens for Minecraft's pause state changes (e.g., when the game menu is opened)
 * and controls the {@link VxClientClock} and client-side {@link VxPhysicsWorld} accordingly.
 *
 * @author xI-Mx-Ix
 */
public class ClientPhysicsPauseHandler {

    // Tracks the pause state from the previous tick to detect changes.
    private static boolean wasPaused = false;

    /**
     * Registers the necessary client tick event listener.
     */
    public static void registerEvents() {
        ClientTickEvent.CLIENT_POST.register(ClientPhysicsPauseHandler::onClientTick);
    }

    /**
     * Called every client tick to check for changes in the game's pause state.
     *
     * @param minecraft The Minecraft client instance.
     */
    private static void onClientTick(Minecraft minecraft) {
        // If not in a world, ensure the clock is running and reset state.
        if (minecraft.level == null) {
            if (wasPaused) {
                VxClientClock.getInstance().resume();
                wasPaused = false;
            }
            return;
        }

        boolean isNowPaused = minecraft.isPaused();

        // If the pause state has changed since the last tick...
        if (isNowPaused != wasPaused) {
            if (isNowPaused) {
                // Game is now paused.
                VxMainClass.LOGGER.debug("Client game is pausing...");
                VxClientClock.getInstance().pause();
                // Also pause any client-side physics simulations.
                VxPhysicsWorld.getAll().forEach(VxPhysicsWorld::pause);
            } else {
                // Game is now resumed.
                VxMainClass.LOGGER.debug("Client game is resuming...");
                VxClientClock.getInstance().resume();
                VxPhysicsWorld.getAll().forEach(VxPhysicsWorld::resume);
            }
            wasPaused = isNowPaused;
        }
    }

    /**
     * Resets the pause handler's state. Should be called when the client disconnects from a server.
     */
    public static void onClientDisconnect() {
        VxClientClock.getInstance().reset();
        wasPaused = false;
    }
}