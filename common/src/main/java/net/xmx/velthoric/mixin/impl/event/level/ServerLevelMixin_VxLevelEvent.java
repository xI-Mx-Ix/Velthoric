/*
This file is part of Velthoric.
Licensed under LGPL 3.0.
*/
package net.xmx.velthoric.mixin.impl.event.level;

import net.minecraft.server.level.ServerLevel;
import net.xmx.velthoric.event.api.VxLevelEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public class ServerLevelMixin_VxLevelEvent {

    @Inject(method = "save", at = @At("RETURN"))
    private void onSave(CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        VxLevelEvent.Save.EVENT.invoker().onLevelSave(new VxLevelEvent.Save(level));
    }
}
