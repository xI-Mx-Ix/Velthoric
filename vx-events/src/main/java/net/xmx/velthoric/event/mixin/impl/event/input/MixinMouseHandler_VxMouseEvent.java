/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.event.mixin.impl.event.input;

import dev.architectury.event.EventResult;
import net.minecraft.client.MouseHandler;
import net.xmx.velthoric.event.api.VxMouseEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to handle mouse input events.
 *
 * @author xI-Mx-Ix
 */
@Mixin(MouseHandler.class)
public class MixinMouseHandler_VxMouseEvent {

    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;

    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void onMousePress(long window, int button, int action, int mods, CallbackInfo ci) {
        VxMouseEvent.Press event = new VxMouseEvent.Press(window, button, action, mods);
        EventResult result = VxMouseEvent.Press.EVENT.invoker().onMousePress(event);
        if (result.isFalse()) {
            ci.cancel();
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        VxMouseEvent.Scroll event = new VxMouseEvent.Scroll(window, horizontal, vertical);
        EventResult result = VxMouseEvent.Scroll.EVENT.invoker().onMouseScroll(event);
        if (result.isFalse()) {
            ci.cancel();
        }
    }

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void onPlayerTurn(CallbackInfo ci) {
        // Only fire the event if there was actual mouse movement
        if (this.accumulatedDX != 0.0D || this.accumulatedDY != 0.0D) {
            VxMouseEvent.Turn event = new VxMouseEvent.Turn(this.accumulatedDX, this.accumulatedDY);
            EventResult result = VxMouseEvent.Turn.EVENT.invoker().onPlayerTurn(event);
            if (result.isFalse()) {
                ci.cancel();
            }
        }
    }
}