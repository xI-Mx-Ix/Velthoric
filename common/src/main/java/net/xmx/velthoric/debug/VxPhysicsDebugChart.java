/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.debug;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.xmx.velthoric.physics.body.client.time.VxFrameTimer;

import java.util.function.ToIntFunction;

/**
 * A utility class for rendering a performance chart for the physics engine.
 * This chart displays the time taken for each physics tick in milliseconds.
 *
 * @author xI-Mx-Ix
 */
public class VxPhysicsDebugChart {

    /**
     * Renders the physics tick performance chart on the screen.
     *
     * @param guiGraphics  The GuiGraphics instance used for rendering.
     * @param font         The Font instance for drawing text.
     * @param frameTimer   The VxFrameTimer instance containing the physics tick data.
     * @param x            The starting X position for the chart.
     * @param width        The width of the chart.
     * @param baseY        The base Y position for the bottom of the chart.
     * @param colorSampler A function that takes a sample value and returns the corresponding color.
     */
    public static void draw(GuiGraphics guiGraphics, Font font, VxFrameTimer frameTimer, int x, int width, int baseY, ToIntFunction<Integer> colorSampler) {
        int recordedFrames = frameTimer.getFrameCount();
        if (recordedFrames == 0) {
            return; // Don't draw anything if there's no data.
        }

        long[] log = frameTimer.getLog();
        int logEnd = frameTimer.getLogEnd();
        final int chartHeight = 60; // Use a height of 60px to be consistent with Minecraft's charts.

        // Determine how many bars to actually draw based on available width and data.
        int barsToDisplay = Math.min(recordedFrames, width);

        long totalTime = 0L;
        int minTime = Integer.MAX_VALUE;
        int maxTime = Integer.MIN_VALUE;

        // Calculate min, max, and average time from the data that will be displayed.
        // We read the log backwards from the last recorded entry.
        for (int i = 0; i < barsToDisplay; ++i) {
            int logIndex = frameTimer.wrapIndex(logEnd - 1 - i);
            int timeMs = (int) (log[logIndex] / 1000000L);
            minTime = Math.min(minTime, timeMs);
            maxTime = Math.max(maxTime, timeMs);
            totalTime += timeMs;
        }

        // Draw the dark background for the chart using the full, fixed width.
        guiGraphics.fill(RenderType.guiOverlay(), x, baseY - chartHeight, x + width, baseY, -1873784752);

        // Draw each bar in the chart, aligned to the right edge.
        for (int i = 0; i < barsToDisplay; ++i) {
            int currentX = x + width - 1 - i;
            int logIndex = frameTimer.wrapIndex(logEnd - 1 - i);

            long durationNanos = log[logIndex];
            // Scale the bar height. Target is 60 physics ticks per second (60 Hz).
            // A 1/60s tick should correspond to a 30px height to leave headroom in the 60px chart.
            int scaledHeight = frameTimer.scaleSampleTo(durationNanos, 30, 60);
            int color = colorSampler.applyAsInt(Mth.clamp(scaledHeight, 0, chartHeight));
            guiGraphics.fill(RenderType.guiOverlay(), currentX, baseY - scaledHeight, currentX + 1, baseY, color);
        }

        // Draw the border around the chart using the full, fixed width.
        guiGraphics.hLine(RenderType.guiOverlay(), x, x + width - 1, baseY - chartHeight, -1);
        guiGraphics.hLine(RenderType.guiOverlay(), x, x + width - 1, baseY - 1, -1);
        guiGraphics.vLine(RenderType.guiOverlay(), x, baseY - chartHeight, baseY, -1);
        guiGraphics.vLine(RenderType.guiOverlay(), x + width - 1, baseY - chartHeight, baseY, -1);

        // Draw the title box and text.
        guiGraphics.fill(RenderType.guiOverlay(), x + 1, baseY - chartHeight + 1, x + 40, baseY - chartHeight + 10, -1873784752);
        guiGraphics.drawString(font, "Physics", x + 2, baseY - chartHeight + 2, 14737632, false);

        // Draw the min, average, and max time text above the chart, aligned to the fixed width.
        String minText = (minTime == Integer.MAX_VALUE ? 0 : minTime) + " ms min";
        String avgText = (barsToDisplay > 0 ? totalTime / (long) barsToDisplay : 0) + " ms avg";
        String maxText = (maxTime == Integer.MIN_VALUE ? 0 : maxTime) + " ms max";
        guiGraphics.drawString(font, minText, x + 2, baseY - chartHeight - 9, 14737632, false);
        guiGraphics.drawCenteredString(font, avgText, x + width / 2, baseY - chartHeight - 9, 14737632);
        guiGraphics.drawString(font, maxText, x + width - font.width(maxText), baseY - chartHeight - 9, 14737632);
    }
}