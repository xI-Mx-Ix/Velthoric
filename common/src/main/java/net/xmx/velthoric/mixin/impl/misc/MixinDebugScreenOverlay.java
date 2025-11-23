/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.misc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.xmx.velthoric.debug.VxPhysicsDebugChart;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to inject physics debug chart rendering into the debug screen overlay.
 * The physics chart is displayed alongside the FPS and TPS charts when debug charts are enabled.
 *
 * @author xI-Mx-Ix
 */
@Mixin(DebugScreenOverlay.class)
public abstract class MixinDebugScreenOverlay {

    @Shadow @Final private Minecraft minecraft;
    @Shadow private boolean renderFpsCharts;

    @Unique
    private VxPhysicsDebugChart velthoric$physicsChart;

    /**
     * Initializes the physics debug chart instance during construction.
     *
     * @param minecraft The Minecraft instance.
     * @param ci The callback info.
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void velthoric_initPhysicsChart(Minecraft minecraft, CallbackInfo ci) {
        // Initialize the chart lazily when first needed
        this.velthoric$physicsChart = null;
    }

    /**
     * Injects rendering logic at the end of the debug screen render method.
     * Displays the physics performance chart when FPS charts are enabled.
     *
     * @param guiGraphics The GuiGraphics instance for rendering.
     * @param ci The callback info.
     */
    @Inject(method = "render", at = @At("RETURN"))
    private void velthoric_renderPhysicsChart(GuiGraphics guiGraphics, CallbackInfo ci) {
        if (this.renderFpsCharts && this.minecraft.level != null) {
            VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(this.minecraft.level.dimension());

            if (physicsWorld != null) {
                // Lazy initialization of the chart
                if (this.velthoric$physicsChart == null) {
                    this.velthoric$physicsChart = new VxPhysicsDebugChart(
                            this.minecraft.font,
                            physicsWorld.getPhysicsFrameTimer()
                    );
                }

                int screenWidth = guiGraphics.guiWidth();
                int screenHeight = guiGraphics.guiHeight();

                // Calculate chart dimensions (same as TPS chart)
                int graphWidth = Math.min(screenWidth / 2, this.velthoric$physicsChart.getWidth(screenWidth / 2));
                int xPos = screenWidth - graphWidth;

                // Position the physics chart below the TPS chart
                // Standard chart height is 60px, with some margin
                int baseY = screenHeight - 70;

                this.velthoric$physicsChart.drawChart(guiGraphics, xPos, graphWidth);
            }
        }
    }
}