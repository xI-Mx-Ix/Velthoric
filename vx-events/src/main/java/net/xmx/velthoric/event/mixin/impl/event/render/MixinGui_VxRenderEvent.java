/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.event.mixin.impl.event.render;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.xmx.velthoric.event.api.VxRenderEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into the main GUI rendering to fire the Velthoric HUD event.
 *
 * @author xI-Mx-Ix
 */
@Mixin(Gui.class)
public class MixinGui_VxRenderEvent {

    @Inject(method = "render", at = @At("TAIL"))
    private void velthoric_fireRenderHudEvent(GuiGraphics guiGraphics, float partialTick, CallbackInfo ci) {
        // Fire the event directly with the partial tick provided by 1.20.1
        VxRenderEvent.ClientRenderHudEvent.EVENT.invoker().onRenderHud(
                new VxRenderEvent.ClientRenderHudEvent(guiGraphics, partialTick)
        );
    }
}