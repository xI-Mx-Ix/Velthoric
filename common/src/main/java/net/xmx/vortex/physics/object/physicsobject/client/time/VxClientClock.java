package net.xmx.vortex.physics.object.physicsobject.client.time;


import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class VxClientClock {

    private static final VxClientClock INSTANCE = new VxClientClock();

    private volatile boolean isPaused = false;
    private long pauseStartTimeNanos = 0L;
    private long totalAccumulatedPauseTimeNanos = 0L;

    private VxClientClock() {}

    public static VxClientClock getInstance() {
        return INSTANCE;
    }

    public synchronized long getGameTimeNanos() {
        if (isPaused) {

            return pauseStartTimeNanos - totalAccumulatedPauseTimeNanos;
        }

        return System.nanoTime() - totalAccumulatedPauseTimeNanos;
    }

    public synchronized void pause() {
        if (!isPaused) {
            isPaused = true;

            pauseStartTimeNanos = System.nanoTime();
        }
    }

    public synchronized void resume() {
        if (isPaused) {

            long pauseDuration = System.nanoTime() - pauseStartTimeNanos;

            totalAccumulatedPauseTimeNanos += pauseDuration;
            isPaused = false;
        }
    }

    public synchronized void reset() {
        isPaused = false;
        pauseStartTimeNanos = 0L;
        totalAccumulatedPauseTimeNanos = 0L;
    }
}