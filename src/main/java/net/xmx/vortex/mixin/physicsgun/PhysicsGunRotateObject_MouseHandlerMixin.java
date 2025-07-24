package net.xmx.vortex.mixin.physicsgun;

import net.minecraft.client.MouseHandler;
import net.xmx.vortex.item.physicsgun.manager.PhysicsGunClientManager;
import net.xmx.vortex.item.physicsgun.packet.PhysicsGunActionPacket;
import net.xmx.vortex.network.NetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class PhysicsGunRotateObject_MouseHandlerMixin {

    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void onTurnPlayer(CallbackInfo ci) {
        var clientManager = PhysicsGunClientManager.getInstance();

        if (clientManager.isRotationMode()) {

            if (this.accumulatedDX != 0.0D || this.accumulatedDY != 0.0D) {
                NetworkHandler.CHANNEL.sendToServer(new PhysicsGunActionPacket((float) this.accumulatedDX, (float) this.accumulatedDY));
            }

            this.accumulatedDX = 0.0D;
            this.accumulatedDY = 0.0D;

            ci.cancel();
        }
    }
}