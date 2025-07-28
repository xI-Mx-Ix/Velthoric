package net.xmx.vortex.mixin.impl.event.level;

import net.minecraft.server.level.ServerLevel;
import net.xmx.vortex.event.api.VxLevelEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public class VxLevelEvent_ServerLevelMixin {

    @Inject(method = "save", at = @At("RETURN"))
    private void onSave(CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        VxLevelEvent.Save.EVENT.invoker().onLevelSave(new VxLevelEvent.Save(level));
    }
}
