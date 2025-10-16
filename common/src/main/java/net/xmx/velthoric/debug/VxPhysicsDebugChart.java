/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.debug;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.FrameTimer;
import net.minecraft.util.Mth;
import net.xmx.velthoric.mixin.impl.misc.accessor.MixinDebugScreenOverlayAccessor;

/**
 * A utility class for rendering a performance chart for the physics engine.
 * This chart displays the time taken for each physics tick in milliseconds.
 */
public class VxPhysicsDebugChart {

    /**
     * Renders the physics tick performance chart on the screen.
     *
     * @param guiGraphics The GuiGraphics instance used for rendering.
     * @param accessor An accessor to the DebugScreenOverlay instance, used to access font and color methods.
     * @param frameTimer The FrameTimer instance containing the physics tick data.
     * @param x The starting X position for the chart.
     * @param width The width of the chart.
     * @param baseY The base Y position for the bottom of the chart.
     */
    public static void draw(GuiGraphics guiGraphics, MixinDebugScreenOverlayAccessor accessor, FrameTimer frameTimer, int x, int width, int baseY) {
        Font font = accessor.getFont();

        int logStart = frameTimer.getLogStart();
        int logEnd = frameTimer.getLogEnd();
        long[] log = frameTimer.getLog();
        int dataLength = log.length - Math.max(0, log.length - width);
        int logIndex = frameTimer.wrapIndex(logStart + Math.max(0, log.length - width));

        long totalTime = 0L;
        int minTime = Integer.MAX_VALUE;
        int maxTime = Integer.MIN_VALUE;

        // Calculate min, max, and average time from the available data.
        for (int i = 0; i < dataLength; ++i) {
            int timeMs = (int) (log[frameTimer.wrapIndex(logIndex + i)] / 1000000L);
            minTime = Math.min(minTime, timeMs);
            maxTime = Math.max(maxTime, timeMs);
            totalTime += timeMs;
        }

        // Draw the dark background for the chart.
        guiGraphics.fill(RenderType.guiOverlay(), x, baseY - 60, x + dataLength, baseY, -1873784752);

        // Draw each bar in the chart, representing a single physics tick.
        int currentX = x;
        while (logIndex != logEnd) {
            int scaledHeight = frameTimer.scaleSampleTo(log[logIndex], 30, 60);
            int color = accessor.invokeGetSampleColor(Mth.clamp(scaledHeight, 0, 60), 0, 30, 60);
            guiGraphics.fill(RenderType.guiOverlay(), currentX, baseY - scaledHeight, currentX + 1, baseY, color);
            ++currentX;
            logIndex = frameTimer.wrapIndex(logIndex + 1);
        }

        // Draw the border around the chart.
        guiGraphics.hLine(RenderType.guiOverlay(), x, x + dataLength - 1, baseY - 60, -1);
        guiGraphics.hLine(RenderType.guiOverlay(), x, x + dataLength - 1, baseY - 1, -1);
        guiGraphics.vLine(RenderType.guiOverlay(), x, baseY - 60, baseY, -1);
        guiGraphics.vLine(RenderType.guiOverlay(), x + dataLength - 1, baseY - 60, baseY, -1);

        // Draw the title box and text.
        guiGraphics.fill(RenderType.guiOverlay(), x + 1, baseY - 60 + 1, x + 40, baseY - 60 + 10, -1873784752);
        guiGraphics.drawString(font, "Physics", x + 2, baseY - 60 + 2, 14737632, false);

        // Draw the min, average, and max time text above the chart.
        String minText = minTime + " ms min";
        String avgText = (dataLength > 0 ? totalTime / (long) dataLength : 0) + " ms avg";
        String maxText = maxTime + " ms max";
        guiGraphics.drawString(font, minText, x + 2, baseY - 60 - 9, 14737632, false);
        guiGraphics.drawCenteredString(font, avgText, x + dataLength / 2, baseY - 60 - 9, 14737632);
        guiGraphics.drawString(font, maxText, x + dataLength - font.width(maxText), baseY - 60 - 9, 14737632);
    }
}