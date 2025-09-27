package net.xmx.velthoric.mixin.impl.riding.input;

import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.xmx.velthoric.network.VxPacketHandler;
import net.xmx.velthoric.physics.riding.VxRidingProxyEntity;
import net.xmx.velthoric.physics.riding.input.C2SRideInputPacket;
import net.xmx.velthoric.physics.riding.input.VxRideInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer_SendInput {

    @Shadow public Input input;

    @Unique
    private VxRideInput velthoric_lastRideInput = null;

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void velthoric_handleRidingInput(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        Entity vehicle = player.getVehicle();

        if (vehicle instanceof VxRidingProxyEntity) {

            VxRideInput currentInput = new VxRideInput(
                this.input.up,
                this.input.down,
                this.input.left,
                this.input.right,
                this.input.jumping,
                this.input.shiftKeyDown
            );

            if (!currentInput.equals(this.velthoric_lastRideInput)) {
                VxPacketHandler.CHANNEL.sendToServer(new C2SRideInputPacket(currentInput));
                this.velthoric_lastRideInput = currentInput;
            }
        } else {

            if (this.velthoric_lastRideInput != null && !this.velthoric_lastRideInput.equals(VxRideInput.NEUTRAL)) {
                VxPacketHandler.CHANNEL.sendToServer(new C2SRideInputPacket(VxRideInput.NEUTRAL));
            }
            this.velthoric_lastRideInput = null;
        }
    }
}