package net.xmx.vortex.mixin.impl.event.debug;

import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.xmx.vortex.event.api.VxDebugEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

@Mixin(DebugScreenOverlay.class)
public class VxAddDebugInfoEvent_DebugScreenOverlayMixin {

    @Inject(method = "getGameInformation", at = @At("RETURN"))
    private void vortex_fireAddDebugInfoEvent(CallbackInfoReturnable<List<String>> cir) {
        List<String> gameInfo = cir.getReturnValue();
        VxDebugEvent.AddDebugInfo.EVENT.invoker().onAddDebugInfo(new VxDebugEvent.AddDebugInfo(gameInfo));
    }
}