/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.event.mixin.impl.event.debug;

import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.timtaran.interactivemc.physics.event.api.VxF3ScreenAdditionEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

/**
 * @author xI-Mx-Ix
 */
@Mixin(DebugScreenOverlay.class)
public class MixinDebugScreenOverlay_VxF3ScreenAdditionEvent {

    @Inject(method = "getGameInformation", at = @At("RETURN"))
    private void velthoric_fireAddDebugInfoEvent(CallbackInfoReturnable<List<String>> cir) {
        List<String> gameInfo = cir.getReturnValue();
        VxF3ScreenAdditionEvent.AddDebugInfo.EVENT.invoker().onAddDebugInfo(new VxF3ScreenAdditionEvent.AddDebugInfo(gameInfo));
    }
}