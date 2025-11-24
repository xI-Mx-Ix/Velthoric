/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.debug;

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
 * Mixin to extend the vanilla DebugScreenOverlay.
 * <p>
 * This class injects the rendering logic for the custom Physics performance chart.
 * It ensures the chart renders alongside the vanilla FPS and TPS charts when the
 * debug overlay (F3) is active with the chart view enabled (F3+2).
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
     * Injects the physics chart rendering at the end of the debug overlay render cycle.
     * <p>
     * The chart is positioned directly above the vanilla TPS chart. The width is
     * clamped to match the standard vanilla chart width (240 pixels) to ensure
     * visual consistency across the UI.
     *
     * @param guiGraphics The graphics context provided by the render method.
     * @param ci          The callback information.
     */
    @Inject(method = "render", at = @At("RETURN"))
    private void velthoric_renderPhysicsChart(GuiGraphics guiGraphics, CallbackInfo ci) {
        // Only render if the FPS/TPS charts are toggled on and the world is active.
        if (this.renderFpsCharts && this.minecraft.level != null) {

            // Retrieve the specific physics world for the current dimension.
            VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(this.minecraft.level.dimension());

            if (physicsWorld != null) {
                var frameTimer = physicsWorld.getPhysicsFrameTimer();

                // Get current screen dimensions to calculate layout.
                int screenWidth = guiGraphics.guiWidth();
                int screenHeight = guiGraphics.guiHeight();

                // Calculate the chart width.
                // Standard vanilla debug charts (FPS/TPS) utilize a sample size of roughly 240.
                // We clamp the width to 240 pixels to match the vanilla charts exactly.
                // We also limit it to half the screen width to support very small window sizes.
                int chartWidth = Math.min(screenWidth / 2, 240);

                // Align the chart to the right edge of the screen.
                int xPos = screenWidth - chartWidth;

                // Calculate the Y position.
                // The vanilla TPS chart is anchored to the bottom of the screen.
                // We offset the physics chart by 75 pixels from the bottom to stack it vertically
                // above the TPS chart.
                int baseY = screenHeight - 75;

                // Define the color mapping function.
                // This converts a bar height (0-60 pixels) into a color in the HSV spectrum.
                // Low values are Green (0.33 hue), High values are Red (0.0 hue).
                ToIntFunction<Integer> colorSampler = (height) -> {
                    float hue = (1.0f - (float) height / 60.0f) / 3.0f;
                    hue = Mth.clamp(hue, 0.0f, 0.33f);
                    // Convert HSB to RGB and ensure the alpha channel is fully opaque (0xFF).
                    return Mth.hsvToRgb(hue, 1.0f, 1.0f) | 0xFF000000;
                };

                // Delegate the actual drawing to the utility class.
                // We pass the calculated static width so the background is drawn correctly
                // even if the physics engine has not yet recorded enough frames to fill it.
                VxPhysicsDebugChart.draw(
                        guiGraphics,
                        this.minecraft.font,
                        frameTimer,
                        xPos,
                        chartWidth,
                        baseY,
                        colorSampler
                );
            }
        }
    }
}