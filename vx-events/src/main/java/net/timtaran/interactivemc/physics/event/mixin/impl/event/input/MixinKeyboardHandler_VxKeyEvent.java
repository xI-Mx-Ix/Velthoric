/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.event.mixin.impl.event.input;

import dev.architectury.event.EventResult;
import net.minecraft.client.KeyboardHandler;
import net.timtaran.interactivemc.physics.event.api.VxKeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to handle keyboard input events.
 *
 * @author xI-Mx-Ix
 */
@Mixin(KeyboardHandler.class)
public class MixinKeyboardHandler_VxKeyEvent {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void onKeyPress(long window, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        VxKeyEvent event = new VxKeyEvent(window, key, scanCode, action, modifiers);
        EventResult result = VxKeyEvent.EVENT.invoker().onKey(event);
        if (result.isFalse()) {
            ci.cancel();
        }
    }
}