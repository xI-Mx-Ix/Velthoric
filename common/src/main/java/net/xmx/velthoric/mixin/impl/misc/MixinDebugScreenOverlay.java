/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.misc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.util.Mth;
import net.xmx.velthoric.debug.VxPhysicsDebugChart;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.ToIntFunction;

/**
 * Mixin to inject physics debug chart rendering into the debug screen overlay.
 * The physics chart is displayed alongside the FPS and TPS charts when debug charts are enabled.
 * It positions the chart strictly above the vanilla TPS chart.
 *
 * @author xI-Mx-Ix
 */
@Mixin(DebugScreenOverlay.class)
public abstract class MixinDebugScreenOverlay {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    private boolean renderFpsCharts;

    /**
     * Injects rendering logic at the end of the debug screen render method.
     * Displays the physics performance chart when FPS charts (F3+2) are enabled.
     *
     * @param guiGraphics The GuiGraphics instance for rendering.
     * @param ci          The callback info.
     */
    @Inject(method = "render", at = @At("RETURN"))
    private void velthoric_renderPhysicsChart(GuiGraphics guiGraphics, CallbackInfo ci) {
        // Only proceed if FPS/TPS charts are enabled and the level is loaded
        if (this.renderFpsCharts && this.minecraft.level != null) {

            // Retrieve the physics world instance for the current dimension
            VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(this.minecraft.level.dimension());

            if (physicsWorld != null) {
                var frameTimer = physicsWorld.getPhysicsFrameTimer();
                int frameCount = frameTimer.getFrameCount();

                // Do not render if there is no data
                if (frameCount <= 0) {
                    return;
                }

                int screenWidth = guiGraphics.guiWidth();
                int screenHeight = guiGraphics.guiHeight();

                // Determine the width of the chart.
                // Standard vanilla charts take up half the screen width.
                // We clamp the width to the number of recorded frames to avoid empty space on the left
                // while the buffer is still filling up.
                int maxGraphWidth = screenWidth / 2;
                int actualWidth = Math.min(maxGraphWidth, frameCount);

                // Align the chart to the right side of the screen
                int xPos = screenWidth - actualWidth;

                // Calculate the Y position.
                // The vanilla TPS chart renders at the bottom of the screen (screenHeight).
                // It has a height of roughly 60px plus padding for text labels (~10-15px).
                // To stack the physics chart on top, we set the base Y to screenHeight - 75.
                int baseY = screenHeight - 75;

                // Define the color sampler function.
                // This converts the bar height (load) into a color.
                // 0 height = Green (Low load), 60 height = Red (Max load).
                ToIntFunction<Integer> colorSampler = (height) -> {
                    // Map height (0-60) to Hue (0.33-0.0)
                    float hue = (1.0f - (float) height / 60.0f) / 3.0f;
                    hue = Mth.clamp(hue, 0.0f, 0.33f);

                    // Convert HSB to RGB and force full opacity (0xFF)
                    return Mth.hsvToRgb(hue, 1.0f, 1.0f) | 0xFF000000;
                };

                // Delegate to the static utility class to draw the chart
                VxPhysicsDebugChart.draw(
                        guiGraphics,
                        this.minecraft.font,
                        frameTimer,
                        xPos,
                        actualWidth,
                        baseY,
                        colorSampler
                );
            }
        }
    }
}