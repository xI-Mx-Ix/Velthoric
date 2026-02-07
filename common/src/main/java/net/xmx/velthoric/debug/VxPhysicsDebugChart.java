/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.debug;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.xmx.velthoric.util.VxFrameTimer;
import org.joml.Matrix4f;

import java.util.function.ToIntFunction;

/**
 * A utility class responsible for rendering the physics performance visualization.
 * <p>
 * This chart displays a history of physics tick durations in milliseconds.
 * It handles coordinate calculations to ensure the chart background,
 * borders, and data bars align precisely with vanilla debug charts.
 *
 * @author xI-Mx-Ix
 */
public class VxPhysicsDebugChart {

    /**
     * Renders the physics tick performance chart on the screen.
     * <p>
     * The rendering process strictly follows the vanilla AbstractDebugChart logic:
     * - The background and borders use the full 'width'.
     * - The data bars are rendered inside the borders (width - 2).
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

        int recordedFrames = frameTimer.getFrameCount();
        long totalTime = 0L;
        int minTime = Integer.MAX_VALUE;
        int maxTime = Integer.MIN_VALUE;
        int barsToDisplay = 0;

        if (recordedFrames > 0) {
            long[] log = frameTimer.getLog();
            int logEnd = frameTimer.getLogEnd();

            // Calculate the number of bars to draw.
            // We subtract 2 pixels from the width to account for the left and right borders,
            // matching vanilla AbstractDebugChart rendering logic.
            int availableBarWidth = width - 2;
            barsToDisplay = Math.min(recordedFrames, availableBarWidth);

            // First Pass: Calculate Statistics (Min/Max/Avg)
            for (int i = 0; i < barsToDisplay; ++i) {
                int logIndex = frameTimer.wrapIndex(logEnd - 1 - i);
                int timeMs = (int) (log[logIndex] / 1000000L);
                minTime = Math.min(minTime, timeMs);
                maxTime = Math.max(maxTime, timeMs);
                totalTime += timeMs;
            }

            // Access the transformation matrix and vertex consumer via the buffer source to enable batch rendering.
            Matrix4f matrix = guiGraphics.pose().last().pose();
            VertexConsumer consumer = guiGraphics.bufferSource().getBuffer(RenderType.guiOverlay());

            // Second Pass: Render the Bars
            // Bars are drawn from right to left using manual vertex construction for efficiency.
            for (int i = 0; i < barsToDisplay; ++i) {
                // Determine the X position.
                // The rightmost bar is located at (x + width - 2).
                // The leftmost possible bar is at (x + 1).
                int currentX = x + width - 2 - i;

                int logIndex = frameTimer.wrapIndex(logEnd - 1 - i);
                long durationNanos = log[logIndex];

                // Scale bar height: 30px height = 60Hz target (16.6ms).
                int scaledHeight = frameTimer.scaleSampleTo(durationNanos, 30, 60);
                int color = colorSampler.applyAsInt(Mth.clamp(scaledHeight, 0, chartHeight));

                // Define coordinates for the vertices of the bar rectangle.
                float x1 = (float) currentX;
                float y1 = (float) (baseY - scaledHeight);
                float x2 = (float) (currentX + 1);
                float y2 = (float) baseY;

                // Build the vertices for a filled quad representing the data bar.
                consumer.addVertex(matrix, x1, y1, 0.0F).setColor(color);
                consumer.addVertex(matrix, x1, y2, 0.0F).setColor(color);
                consumer.addVertex(matrix, x2, y2, 0.0F).setColor(color);
                consumer.addVertex(matrix, x2, y1, 0.0F).setColor(color);
            }
        }

        // Horizontal lines (Top and Bottom)
        guiGraphics.hLine(RenderType.guiOverlay(), x, x + width - 1, baseY - chartHeight, -1);
        guiGraphics.hLine(RenderType.guiOverlay(), x, x + width - 1, baseY - 1, -1);

        // Vertical lines (Left and Right)
        guiGraphics.vLine(RenderType.guiOverlay(), x, baseY - chartHeight, baseY, -1);
        guiGraphics.vLine(RenderType.guiOverlay(), x + width - 1, baseY - chartHeight, baseY, -1);

        // Render Text Labels
        guiGraphics.drawString(font, "Physics", x + 2, baseY - chartHeight + 2, 14737632, false);

        if (barsToDisplay > 0) {
            String minText = minTime + " ms min";
            String avgText = (totalTime / (long) barsToDisplay) + " ms avg";
            String maxText = maxTime + " ms max";

            guiGraphics.drawString(font, minText, x + 2, baseY - chartHeight - 9, 14737632, false);
            guiGraphics.drawCenteredString(font, avgText, x + width / 2, baseY - chartHeight - 9, 14737632);
            // Align max text with the inner right border edge (width - 2)
            guiGraphics.drawString(font, maxText, x + width - font.width(maxText) - 2, baseY - chartHeight - 9, 14737632);
        } else {
            guiGraphics.drawString(font, "0 ms min", x + 2, baseY - chartHeight - 9, 14737632, false);
            guiGraphics.drawCenteredString(font, "0 ms avg", x + width / 2, baseY - chartHeight - 9, 14737632);
            String maxText = "0 ms max";
            guiGraphics.drawString(font, maxText, x + width - font.width(maxText) - 2, baseY - chartHeight - 9, 14737632);
        }
    }
}