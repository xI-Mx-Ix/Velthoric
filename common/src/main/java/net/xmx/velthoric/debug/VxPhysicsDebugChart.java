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
 * A utility class responsible for rendering the physics performance visualization.
 * <p>
 * This chart displays a history of physics tick durations in milliseconds.
 * It handles the layering of background, data bars, and overlay borders to ensure
 * a clean visual presentation.
 *
 * @author xI-Mx-Ix
 */
public class VxPhysicsDebugChart {

    /**
     * Renders the physics tick performance chart on the screen.
     * <p>
     * The rendering order is strictly defined to prevent visual artifacts:
     * 1. Backgrounds (Chart area and Title box)
     * 2. Data Bars (The dynamic content)
     * 3. Borders (White outlines, drawn last to sit on top of the bars)
     * 4. Text Labels
     *
     * @param guiGraphics  The Minecraft GuiGraphics instance used for rendering primitives.
     * @param font         The text renderer instance.
     * @param frameTimer   The data source containing the circular buffer of frame times.
     * @param x            The absolute X coordinate of the left edge of the chart.
     * @param width        The fixed width of the chart in pixels.
     * @param baseY        The absolute Y coordinate of the bottom edge of the chart.
     * @param colorSampler A functional interface determining the bar color based on its height/load.
     */
    public static void draw(GuiGraphics guiGraphics, Font font, VxFrameTimer frameTimer, int x, int width, int baseY, ToIntFunction<Integer> colorSampler) {
        // Define the constant height of the chart area in pixels.
        final int chartHeight = 60;

        // Draw the semi-transparent dark background rect for the entire chart area.
        guiGraphics.fill(RenderType.guiOverlay(), x, baseY - chartHeight, x + width, baseY, -1873784752);

        // Draw the background box for the "Physics" title to ensure text readability.
        guiGraphics.fill(RenderType.guiOverlay(), x + 1, baseY - chartHeight + 1, x + 40, baseY - chartHeight + 10, -1873784752);

        int recordedFrames = frameTimer.getFrameCount();
        long totalTime = 0L;
        int minTime = Integer.MAX_VALUE;
        int maxTime = Integer.MIN_VALUE;
        int barsToDisplay = 0;

        // Only process and render bars if data exists.
        if (recordedFrames > 0) {
            long[] log = frameTimer.getLog();
            int logEnd = frameTimer.getLogEnd();

            // Determine the number of bars to draw, limited by chart width.
            barsToDisplay = Math.min(recordedFrames, width);

            // First Pass: Calculate Statistics (Min/Max/Avg)
            for (int i = 0; i < barsToDisplay; ++i) {
                int logIndex = frameTimer.wrapIndex(logEnd - 1 - i);
                int timeMs = (int) (log[logIndex] / 1000000L);
                minTime = Math.min(minTime, timeMs);
                maxTime = Math.max(maxTime, timeMs);
                totalTime += timeMs;
            }

            // Second Pass: Render the Bars
            // We draw these BEFORE the borders so that if a bar reaches the edge,
            // the white border line draws OVER it, maintaining a clean outline.
            for (int i = 0; i < barsToDisplay; ++i) {
                // Determine X position: Start from right edge and move left.
                int currentX = x + width - 1 - i;
                int logIndex = frameTimer.wrapIndex(logEnd - 1 - i);

                long durationNanos = log[logIndex];

                // Scale bar height: 30px height = 60Hz target (16.6ms).
                int scaledHeight = frameTimer.scaleSampleTo(durationNanos, 30, 60);

                // Determine color based on load.
                int color = colorSampler.applyAsInt(Mth.clamp(scaledHeight, 0, chartHeight));

                // Draw the bar.
                // Note: The bottom Y coordinate is 'baseY'. The fill method excludes the max coordinate,
                // but pixel alignment often causes the last row to overlap with the border line position.
                guiGraphics.fill(RenderType.guiOverlay(), currentX, baseY - scaledHeight, currentX + 1, baseY, color);
            }
        }

        // Draw the white border lines AFTER the bars.
        // This ensures the borders are always visible and not overwritten by the colored bars
        // on the rightmost column or bottommost row.

        // Horizontal lines (top and bottom)
        guiGraphics.hLine(RenderType.guiOverlay(), x, x + width - 1, baseY - chartHeight, -1);
        guiGraphics.hLine(RenderType.guiOverlay(), x, x + width - 1, baseY - 1, -1);

        // Vertical lines (left and right)
        guiGraphics.vLine(RenderType.guiOverlay(), x, baseY - chartHeight, baseY, -1);
        guiGraphics.vLine(RenderType.guiOverlay(), x + width - 1, baseY - chartHeight, baseY, -1);

        // Title
        guiGraphics.drawString(font, "Physics", x + 2, baseY - chartHeight + 2, 14737632, false);

        // Statistics (only if data exists, otherwise default to 0)
        String minText = (minTime == Integer.MAX_VALUE ? 0 : minTime) + " ms min";
        String avgText = (barsToDisplay > 0 ? totalTime / (long) barsToDisplay : 0) + " ms avg";
        String maxText = (maxTime == Integer.MIN_VALUE ? 0 : maxTime) + " ms max";

        guiGraphics.drawString(font, minText, x + 2, baseY - chartHeight - 9, 14737632, false);
        guiGraphics.drawCenteredString(font, avgText, x + width / 2, baseY - chartHeight - 9, 14737632);
        guiGraphics.drawString(font, maxText, x + width - font.width(maxText), baseY - chartHeight - 9, 14737632);
    }
}