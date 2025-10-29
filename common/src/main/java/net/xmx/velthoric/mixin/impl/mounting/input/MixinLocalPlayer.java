/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.mounting.input;

import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.xmx.velthoric.network.VxPacketHandler;
import net.xmx.velthoric.physics.mounting.entity.VxMountingEntity;
import net.xmx.velthoric.physics.mounting.input.C2SMountInputPacket;
import net.xmx.velthoric.physics.mounting.input.VxMountInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author xI-Mx-Ix
 */
@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer {

    @Shadow public Input input;

    @Unique
    private VxMountInput velthoric_lastRideInput = null;

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void velthoric_handleRidingInput(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        Entity vehicle = player.getVehicle();

        if (vehicle instanceof VxMountingEntity) {

            VxMountInput currentInput = new VxMountInput(
                this.input.up,
                this.input.down,
                this.input.left,
                this.input.right,
                this.input.jumping,
                this.input.shiftKeyDown
            );

            if (!currentInput.equals(this.velthoric_lastRideInput)) {
                VxPacketHandler.sendToServer(new C2SMountInputPacket(currentInput));
                this.velthoric_lastRideInput = currentInput;
            }
        } else {

            if (this.velthoric_lastRideInput != null && !this.velthoric_lastRideInput.equals(VxMountInput.NEUTRAL)) {
                VxPacketHandler.sendToServer(new C2SMountInputPacket(VxMountInput.NEUTRAL));
            }
            this.velthoric_lastRideInput = null;
        }
    }
}