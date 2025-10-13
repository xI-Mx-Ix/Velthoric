/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.world;

/**
 * A utility class to hold the global physics pause state.
 * This state is set by the client thread (in MixinMinecraft) and read
 * by the physics thread (in VxPhysicsWorld).
 * The 'paused' field is volatile to ensure visibility across threads.
 *
 * @author xI-Mx-Ix
 */
public final class VxPauseUtil {

    private static volatile boolean paused = false;

    /**
     * Private constructor to prevent instantiation.
     */
    private VxPauseUtil() {
    }

    /**
     * Sets the global pause state for the physics simulation.
     * @param isPaused True to pause the simulation, false to resume.
     */
    public static void setPaused(boolean isPaused) {
        paused = isPaused;
    }

    /**
     * Checks if the physics simulation is currently paused.
     * @return True if paused, otherwise false.
     */
    public static boolean isPaused() {
        return paused;
    }
}