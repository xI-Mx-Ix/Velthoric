package net.xmx.xbullet.mixin.riding;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.xmx.xbullet.physics.object.riding.ClientPlayerRidingSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;

    @Shadow public abstract boolean isMouseGrabbed();

    @Shadow private Minecraft minecraft;

    @Inject(
            method = "turnPlayer()V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void xbullet_onTurnPlayer(CallbackInfo ci) {
        if (ClientPlayerRidingSystem.isRiding()) {

            ci.cancel();

            if (this.isMouseGrabbed() && this.minecraft.isWindowActive()) {
                double d4 = this.minecraft.options.sensitivity().get() * 0.6F + 0.2F;
                double d5 = d4 * d4 * d4 * 8.0D;

                double dX = this.accumulatedDX * d5;
                double dY = this.accumulatedDY * d5;

                ClientPlayerRidingSystem.updateLocalLook(dX, dY);

                this.accumulatedDX = 0.0D;
                this.accumulatedDY = 0.0D;
            }
        }
    }
}