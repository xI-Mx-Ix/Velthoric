/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.client.time;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * A client-side clock that provides the current time in nanoseconds, accounting for game pauses.
 * This is crucial for ensuring that interpolation continues correctly after the game is unpaused,
 * as it effectively stops time while paused.
 *
 * @author xI-Mx-Ix
 */
@Environment(EnvType.CLIENT)
public enum VxClientClock {
    INSTANCE;

    // Flag indicating if the clock is currently paused. Volatile for thread safety.
    private volatile boolean isPaused = false;
    // The timestamp (from System.nanoTime()) when the clock was last paused.
    private long pauseStartTimeNanos = 0L;
    // The total accumulated time the clock has been paused.
    private long totalAccumulatedPauseTimeNanos = 0L;

    /**
     * Gets the current game time in nanoseconds.
     * If the clock is paused, it returns the time at which it was paused.
     * Otherwise, it returns the current system time minus the total accumulated pause time.
     *
     * @return The current, pause-adjusted game time in nanoseconds.
     */
    public synchronized long getGameTimeNanos() {
        if (isPaused) {
            // If paused, time is frozen at the moment of pausing.
            return pauseStartTimeNanos - totalAccumulatedPauseTimeNanos;
        }
        // If running, the effective time is the system time minus all previous pause durations.
        return System.nanoTime() - totalAccumulatedPauseTimeNanos;
    }

    /**
     * Pauses the clock. If already paused, this method has no effect.
     */
    public synchronized void pause() {
        if (!isPaused) {
            isPaused = true;
            // Record the exact time the pause began.
            pauseStartTimeNanos = System.nanoTime();
        }
    }

    /**
     * Resumes the clock. If already running, this method has no effect.
     */
    public synchronized void resume() {
        if (isPaused) {
            // Calculate the duration of the just-ended pause.
            long pauseDuration = System.nanoTime() - pauseStartTimeNanos;
            // Add it to the total accumulated pause time.
            totalAccumulatedPauseTimeNanos += pauseDuration;
            isPaused = false;
        }
    }

    /**
     * Resets the clock to its initial state. Called on disconnect.
     */
    public synchronized void reset() {
        isPaused = false;
        pauseStartTimeNanos = 0L;
        totalAccumulatedPauseTimeNanos = 0L;
    }
}