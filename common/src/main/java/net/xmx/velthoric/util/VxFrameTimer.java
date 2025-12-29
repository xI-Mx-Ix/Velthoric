/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.util;

/**
 * A custom implementation for recording timing data, designed to replace
 * Minecraft's FrameTimer for compatibility purposes. This class captures
 * timing information in a circular buffer, allowing for performance analysis
 * and chart rendering without being tied to a specific Minecraft version.
 *
 * @author xI-Mx-Ix
 */
public final class VxFrameTimer {
    /**
     * The total number of data points to be stored in the history.
     */
    private static final int HISTORY_LENGTH = 240;

    /**
     *  An array to store frame durations in nanoseconds.
     */
    private final long[] frameTimes = new long[HISTORY_LENGTH];

    /**
     * The index of the oldest frame timing in the buffer.
     */
    private int startIndex = 0;

    /**
     * The total number of recorded frames, capped at HISTORY_LENGTH.
     */
    private int logLength = 0;

    /**
     * The index of the most recently added frame timing.
     */
    private int currentIndex = 0;

    /**
     * Records a new frame duration, adding it to the circular buffer.
     *
     * @param frameDurationNanos The duration of the frame in nanoseconds.
     */
    public void logFrameDuration(long frameDurationNanos) {
        // Store the new duration at the current position.
        this.frameTimes[this.currentIndex] = frameDurationNanos;
        // Advance the index for the next recording.
        this.currentIndex = (this.currentIndex + 1) % HISTORY_LENGTH;

        if (this.logLength < HISTORY_LENGTH) {
            // If the buffer is not yet full, simply increment the count.
            this.logLength++;
        } else {
            // If the buffer is full, the start index must also advance,
            // making it behave like a circular buffer.
            this.startIndex = (this.startIndex + 1) % HISTORY_LENGTH;
        }
    }

    /**
     * Scales a given time sample to a specific height for chart rendering.
     *
     * @param durationNanos  The duration in nanoseconds to be scaled.
     * @param targetHeight   The target height for the scaled value (e.g., in pixels).
     * @param referenceTps   The reference value for ticks-per-second (e.g., 60).
     * @return The scaled integer value representing the height.
     */
    public int scaleSampleTo(long durationNanos, int targetHeight, int referenceTps) {
        // The calculation is based on the ratio of the actual duration to the target duration for the reference TPS.
        final double nanosecondsPerTick = 1_000_000_000.0 / referenceTps;
        final double performanceRatio = (double) durationNanos / nanosecondsPerTick;
        return (int) (performanceRatio * targetHeight);
    }

    /**
     * Returns the index of the oldest recorded frame timing.
     *
     * @return The starting index in the circular buffer.
     */
    public int getLogStart() {
        return this.startIndex;
    }

    /**
     * Returns the index where the next frame timing will be written.
     *
     * @return The end index (current write position) in the circular buffer.
     */
    public int getLogEnd() {
        return this.currentIndex;
    }

    /**
     * A utility method to wrap an index around the buffer length. This now correctly
     * handles negative inputs to prevent out-of-bounds exceptions.
     *
     * @param index The index to be wrapped.
     * @return The wrapped index, guaranteed to be within the [0, HISTORY_LENGTH - 1] range.
     */
    public int wrapIndex(int index) {
        // This ensures the result is always non-negative.
        return (index % HISTORY_LENGTH + HISTORY_LENGTH) % HISTORY_LENGTH;
    }

    /**
     * Returns the raw array of logged frame times.
     *
     * @return The long array containing the frame time data.
     */
    public long[] getLog() {
        return this.frameTimes;
    }

    /**
     * Returns the total number of frames currently stored in the timer.
     *
     * @return The number of recorded frames, up to a maximum of HISTORY_LENGTH.
     */
    public int getFrameCount() {
        return this.logLength;
    }
}