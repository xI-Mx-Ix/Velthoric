/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.misc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.FrameTimer;
import net.minecraft.util.Mth;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugScreenOverlay.class)
public abstract class MixinDebugScreenOverlay {

    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private Font font;
    @Shadow protected abstract int getSampleColor(int i, int j, int k, int l);

    @Inject(method = "render", at = @At("RETURN"))
    private void onRenderEnd(GuiGraphics guiGraphics, CallbackInfo ci) {

        if (this.minecraft.options.renderFpsChart) {
            VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(this.minecraft.level.dimension());

            if (physicsWorld != null) {
                int screenWidth = guiGraphics.guiWidth();
                int screenHeight = guiGraphics.guiHeight();

                int graphWidth = Math.min(screenWidth / 2, 240);
                int xPos = screenWidth - graphWidth;

                int baseY = screenHeight - 70;

                drawPhysicsTickChart(guiGraphics, physicsWorld.getPhysicsFrameTimer(), xPos, graphWidth, baseY);
            }
        }
    }

    private void drawPhysicsTickChart(GuiGraphics guiGraphics, FrameTimer frameTimer, int x, int width, int baseY) {
        int logStart = frameTimer.getLogStart();
        int logEnd = frameTimer.getLogEnd();
        long[] log = frameTimer.getLog();
        int dataLength = log.length - Math.max(0, log.length - width);
        int logIndex = frameTimer.wrapIndex(logStart + Math.max(0, log.length - width));

        long totalTime = 0L;
        int minTime = Integer.MAX_VALUE;
        int maxTime = Integer.MIN_VALUE;

        for (int i = 0; i < dataLength; ++i) {
            int timeMs = (int) (log[frameTimer.wrapIndex(logIndex + i)] / 1000000L);
            minTime = Math.min(minTime, timeMs);
            maxTime = Math.max(maxTime, timeMs);
            totalTime += timeMs;
        }

        guiGraphics.fill(RenderType.guiOverlay(), x, baseY - 60, x + dataLength, baseY, -1873784752);

        int currentX = x;
        while (logIndex != logEnd) {
            int scaledHeight = frameTimer.scaleSampleTo(log[logIndex], 30, 60);
            int color = this.getSampleColor(Mth.clamp(scaledHeight, 0, 60), 0, 30, 60);
            guiGraphics.fill(RenderType.guiOverlay(), currentX, baseY - scaledHeight, currentX + 1, baseY, color);
            ++currentX;
            logIndex = frameTimer.wrapIndex(logIndex + 1);
        }

        guiGraphics.hLine(RenderType.guiOverlay(), x, x + dataLength - 1, baseY - 60, -1);

        guiGraphics.hLine(RenderType.guiOverlay(), x, x + dataLength - 1, baseY - 1, -1);

        guiGraphics.vLine(RenderType.guiOverlay(), x, baseY - 60, baseY, -1);

        guiGraphics.vLine(RenderType.guiOverlay(), x + dataLength - 1, baseY - 60, baseY, -1);

        guiGraphics.fill(RenderType.guiOverlay(), x + 1, baseY - 60 + 1, x + 40, baseY - 60 + 10, -1873784752);
        guiGraphics.drawString(this.font, "Physics", x + 2, baseY - 60 + 2, 14737632, false);

        String minText = minTime + " ms min";
        String avgText = (dataLength > 0 ? totalTime / (long) dataLength : 0) + " ms avg";
        String maxText = maxTime + " ms max";
        guiGraphics.drawString(this.font, minText, x + 2, baseY - 60 - 9, 14737632, false);
        guiGraphics.drawCenteredString(this.font, avgText, x + dataLength / 2, baseY - 60 - 9, 14737632);
        guiGraphics.drawString(this.font, maxText, x + dataLength - this.font.width(maxText), baseY - 60 - 9, 14737632);
    }
}