/*
This file is part of Velthoric.
Licensed under LGPL 3.0.
*/
package net.xmx.velthoric.mixin.impl.event.debug;

import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.xmx.velthoric.event.api.VxDebugEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

@Mixin(DebugScreenOverlay.class)
public class DebugScreenOverlayMixin_VxAddDebugInfoEvent {

    @Inject(method = "getGameInformation", at = @At("RETURN"))
    private void velthoric_fireAddDebugInfoEvent(CallbackInfoReturnable<List<String>> cir) {
        List<String> gameInfo = cir.getReturnValue();
        VxDebugEvent.AddDebugInfo.EVENT.invoker().onAddDebugInfo(new VxDebugEvent.AddDebugInfo(gameInfo));
    }
}