/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.debug;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.debugchart.AbstractDebugChart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.util.debugchart.SampleStorage;
import net.xmx.velthoric.physics.body.client.time.VxFrameTimer;

import java.util.Locale;

/**
 * A debug chart implementation for visualizing physics engine performance.
 * Extends Minecraft's AbstractDebugChart to maintain consistency with the vanilla debug overlay.
 * This chart displays the time taken for each physics tick in milliseconds.
 *
 * @author xI-Mx-Ix
 */
public class VxPhysicsDebugChart extends AbstractDebugChart {

    private static final int COLOR_RED = 0xFFFF0000;
    private static final int COLOR_YELLOW = 0xFFFFFF00;
    private static final int COLOR_GREEN = 0xFF00FF00;

    /**
     * Target physics tick rate in Hz (ticks per second).
     * At 60 Hz, each tick should take approximately 16.67ms.
     */
    private static final int TARGET_TICK_RATE = 60;

    /**
     * Target time per tick in milliseconds (1000ms / 60 ticks = 16.67ms per tick).
     */
    private static final double TARGET_TIME_MS = 1000.0 / TARGET_TICK_RATE;

    private final VxFrameTimer frameTimer;

    /**
     * Constructs a new physics debug chart.
     *
     * @param font The font to use for rendering text.
     * @param frameTimer The frame timer that provides physics timing data.
     */
    public VxPhysicsDebugChart(Font font, VxFrameTimer frameTimer) {
        super(font, new PhysicsSampleStorage(frameTimer));
        this.frameTimer = frameTimer;
    }

    @Override
    protected void renderAdditionalLinesAndLabels(GuiGraphics guiGraphics, int x, int width, int height) {
        // Draw the chart title
        this.drawStringWithShade(guiGraphics, "Physics", x + 1, height - 60 + 1);

        // Draw reference lines for 30 FPS equivalent (33.33ms) and 60 FPS equivalent (16.67ms)
        int thirtyFpsHeight = this.getSampleHeight(TARGET_TIME_MS * 2.0); // ~33ms line
        int sixtyFpsHeight = this.getSampleHeight(TARGET_TIME_MS);        // ~17ms line

        this.drawStringWithShade(guiGraphics, "30 Hz", x + 1, height - thirtyFpsHeight - 8);
        guiGraphics.hLine(RenderType.guiOverlay(), x, x + width - 1, height - thirtyFpsHeight, 0xFF888888);

        this.drawStringWithShade(guiGraphics, "60 Hz", x + 1, height - sixtyFpsHeight - 8);
        guiGraphics.hLine(RenderType.guiOverlay(), x, x + width - 1, height - sixtyFpsHeight, 0xFF888888);
    }

    @Override
    protected String toDisplayString(double value) {
        return String.format(Locale.ROOT, "%.1f ms", toMilliseconds(value));
    }

    @Override
    protected int getSampleHeight(double value) {
        // Scale the height so that TARGET_TIME_MS corresponds to half the chart height (30px)
        // This gives us headroom to see spikes above the target tick time
        double ms = toMilliseconds(value);
        return (int) Math.round((ms / TARGET_TIME_MS) * 30.0);
    }

    @Override
    protected int getSampleColor(long value) {
        double ms = toMilliseconds((double) value);

        // Color gradient:
        // Green: 0ms - TARGET_TIME_MS (good performance, at or below target)
        // Yellow: TARGET_TIME_MS - TARGET_TIME_MS*2 (acceptable performance)
        // Red: TARGET_TIME_MS*2+ (poor performance, significantly over target)
        return this.getSampleColor(
                ms,
                0.0,                    // Min value (green starts)
                COLOR_GREEN,
                TARGET_TIME_MS,         // Mid value (yellow starts)
                COLOR_YELLOW,
                TARGET_TIME_MS * 2.0,   // Max value (red starts)
                COLOR_RED
        );
    }

    /**
     * Converts a nanosecond value to milliseconds.
     *
     * @param nanos The value in nanoseconds.
     * @return The value in milliseconds.
     */
    private static double toMilliseconds(double nanos) {
        return nanos / 1_000_000.0;
    }

    /**
     * Custom SampleStorage implementation that wraps VxFrameTimer.
     * This adapter allows the physics frame timer to be used with AbstractDebugChart.
     */
    private static class PhysicsSampleStorage implements SampleStorage {
        private final VxFrameTimer frameTimer;

        public PhysicsSampleStorage(VxFrameTimer frameTimer) {
            this.frameTimer = frameTimer;
        }

        @Override
        public long get(int index) {
            if (index < 0 || index >= size()) {
                return 0L;
            }
            // Convert from oldest-to-newest index to the frame timer's internal indexing
            int logEnd = frameTimer.getLogEnd();
            int frameCount = frameTimer.getFrameCount();
            int logIndex = frameTimer.wrapIndex(logEnd - frameCount + index);
            return frameTimer.getLog()[logIndex];
        }

        @Override
        public long get(int index, int dimension) {
            // Physics chart only has one dimension, so we ignore the dimension parameter
            return get(index);
        }

        @Override
        public int size() {
            return frameTimer.getFrameCount();
        }

        @Override
        public int capacity() {
            return frameTimer.getLog().length;
        }

        @Override
        public void reset() {
            frameTimer.reset();
        }
    }
}