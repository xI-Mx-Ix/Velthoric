/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.world;

import net.minecraft.client.multiplayer.ClientLevel;
import net.xmx.velthoric.bridge.mounting.manager.VxClientMountingManager;
import net.xmx.velthoric.physics.body.client.VxClientBodyManager;
import net.xmx.velthoric.physics.body.client.time.VxClientClock;
import org.jetbrains.annotations.Nullable;

/**
 * The central singleton managing the client-side physics simulation state.
 * <p>
 * This class acts as the container for all client-side physics subsystems.
 * Unlike the server side, where multiple worlds exist, the client typically has only one
 * active physics context. This singleton persists across level changes but clears its
 * data when switching worlds.
 *
 * @author xI-Mx-Ix
 */
public class VxClientPhysicsWorld {

    private static final VxClientPhysicsWorld INSTANCE = new VxClientPhysicsWorld();

    private final VxClientClock clock;
    private final VxClientBodyManager bodyManager;
    private final VxClientMountingManager mountingManager;

    @Nullable
    private ClientLevel level;

    /**
     * Private constructor to enforce singleton pattern.
     * Initializes the subsystems with a reference to this world.
     */
    private VxClientPhysicsWorld() {
        this.clock = new VxClientClock();
        this.mountingManager = new VxClientMountingManager();
        // Pass 'this' so managers can access the clock and each other
        this.bodyManager = new VxClientBodyManager(this);
    }

    /**
     * @return The singleton instance of the client physics world.
     */
    public static VxClientPhysicsWorld getInstance() {
        return INSTANCE;
    }

    /**
     * Called when a client level is loaded.
     * Sets the current level context and resets the subsystems.
     *
     * @param level The new client level.
     */
    public void onLevelLoad(ClientLevel level) {
        // Ensure clean state before starting a new level
        this.clear();
        this.level = level;
    }

    /**
     * Called when the client level is unloaded.
     * Clears all data and removes the level reference.
     */
    public void onLevelUnload() {
        this.clear();
        this.level = null;
    }

    /**
     * Called every client tick to update the physics subsystems.
     * Handles the synchronization of the clock based on the game's pause state.
     *
     * @param isPaused True if the Minecraft client is currently paused.
     */
    public void tick(boolean isPaused) {
        if (isPaused) {
            this.clock.pause();
        } else {
            this.clock.resume();

            // Only tick the body manager if we have a valid level and are running
            if (this.level != null) {
                this.bodyManager.clientTick();
            }
        }
    }

    /**
     * Clears all data in the subsystems.
     */
    public void clear() {
        this.bodyManager.clearAll();
        this.mountingManager.clearAll();
        this.clock.reset();
    }

    /**
     * @return The currently active Minecraft client level, or null if none is loaded.
     */
    @Nullable
    public ClientLevel getLevel() {
        return level;
    }

    /**
     * @return The client-side clock used for interpolation and synchronization.
     */
    public VxClientClock getClock() {
        return clock;
    }

    /**
     * @return The manager responsible for physics bodies on the client.
     */
    public VxClientBodyManager getBodyManager() {
        return bodyManager;
    }

    /**
     * @return The manager responsible for mountable seats on the client.
     */
    public VxClientMountingManager getMountingManager() {
        return mountingManager;
    }
}