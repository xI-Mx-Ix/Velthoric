/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.misc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.xmx.velthoric.debug.VxPhysicsDebugChart;
import net.xmx.velthoric.mixin.impl.misc.accessor.MixinDebugScreenOverlayAccessor;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author xI-Mx-Ix
 */
@Mixin(DebugScreenOverlay.class)
public abstract class MixinDebugScreenOverlay {

    @Shadow @Final private Minecraft minecraft;

    /**
     * Injects a call at the end of the render method to draw the physics chart.
     *
     * @param guiGraphics The GuiGraphics instance for rendering.
     * @param ci The callback info.
     */
    @Inject(method = "render", at = @At("RETURN"))
    private void onRenderEnd(GuiGraphics guiGraphics, CallbackInfo ci) {
        if (this.minecraft.options.renderFpsChart) {
            VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(this.minecraft.level.dimension());

            if (physicsWorld != null) {
                int screenWidth = guiGraphics.guiWidth();
                int screenHeight = guiGraphics.guiHeight();

                int graphWidth = Math.min(screenWidth / 2, 240);
                int xPos = screenWidth - graphWidth;

                // The base Y position for the chart, leaving space for other elements.
                int baseY = screenHeight - 70;

                // Cast this instance to the accessor to provide access to private members.
                MixinDebugScreenOverlayAccessor accessor = (MixinDebugScreenOverlayAccessor) this;
                VxPhysicsDebugChart.draw(guiGraphics, accessor, physicsWorld.getPhysicsFrameTimer(), xPos, graphWidth, baseY);
            }
        }
    }
}