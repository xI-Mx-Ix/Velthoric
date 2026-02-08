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
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.ToIntFunction;

/**
 * Mixin to extend the DebugScreenOverlay.
 * <p>
 * This class injects the rendering logic for the custom Physics performance chart.
 * It ensures the chart renders alongside existing debug charts when the
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
     * The chart is positioned directly above the TPS chart area.
     *
     * @param guiGraphics The graphics context provided by the render method.
     * @param ci          The callback information.
     */
    @Inject(method = "render", at = @At("RETURN"))
    private void velthoric_renderPhysicsChart(GuiGraphics guiGraphics, CallbackInfo ci) {
        if (this.renderFpsCharts && this.minecraft.level != null) {

            // Retrieve the specific physics world for the current dimension.
            VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(this.minecraft.level.dimension());

            if (physicsWorld != null) {
                var frameTimer = physicsWorld.getPhysicsFrameTimer();

                // Get current screen dimensions.
                int screenWidth = guiGraphics.guiWidth();
                int screenHeight = guiGraphics.guiHeight();

                int halfWidth = screenWidth / 2;

                // Calculate the chart width to match vanilla AbstractDebugChart behavior.
                // Vanilla charts calculate width as 'capacity + 2' (240 + 2 = 242).
                // We use 240 + 2 to ensure the physics chart aligns perfectly with the TPS chart.
                int chartWidth = Math.min(halfWidth, 240 + 2);

                // Calculate the X position.
                // The chart is right-aligned.
                int xPos = screenWidth - chartWidth;

                // Calculate the Y position.
                // Existing charts are anchored to the bottom. We offset the physics chart
                // by 75 pixels from the bottom to stack it vertically above the lower chart.
                int baseY = screenHeight - 75;

                // Define the color mapping function.
                ToIntFunction<Integer> colorSampler = (height) -> {
                    float hue = (1.0f - (float) height / 60.0f) / 3.0f;
                    hue = Mth.clamp(hue, 0.0f, 0.33f);
                    return Mth.hsvToRgb(hue, 1.0f, 1.0f) | 0xFF000000;
                };

                // Delegate the actual drawing to the utility class.
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