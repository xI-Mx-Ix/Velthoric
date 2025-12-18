/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.mixin.impl.mounting.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.timtaran.interactivemc.physics.network.VxPacketHandler;
import net.timtaran.interactivemc.physics.physics.mounting.entity.VxMountingEntity;
import net.timtaran.interactivemc.physics.physics.mounting.input.C2SMountInputPacket;
import net.timtaran.interactivemc.physics.physics.mounting.input.VxMountInput;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into the local player logic to capture and transmit custom vehicle inputs
 * when the player is mounting a physics entity.
 *
 * @author xI-Mx-Ix
 */
@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer {

    @Unique
    private VxMountInput velthoric_lastRideInput = null;

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void velthoric_handleRidingInput(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        Entity vehicle = player.getVehicle();

        if (vehicle instanceof VxMountingEntity) {
            // Retrieve the window handle to check raw keyboard input
            long window = Minecraft.getInstance().getWindow().getWindow();

            // 1. Calculate Analog Axis (Forward/Backward) using raw GLFW keys
            // W = Forward (+1.0), S = Backward (-1.0)
            float forward = 0.0f;
            if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_W)) forward += 1.0f;
            if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_S)) forward -= 1.0f;

            // 2. Calculate Analog Axis (Left/Right) using raw GLFW keys
            // A = Left (-1.0), D = Right (+1.0)
            float right = 0.0f;
            if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_A)) right -= 1.0f;
            if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_D)) right += 1.0f;

            // 3. Calculate Action Flags Bitmask
            int flags = 0;
            // Handbrake = Space
            if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_SPACE)) flags |= VxMountInput.FLAG_HANDBRAKE;

            // Shift Up = R
            if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_R)) flags |= VxMountInput.FLAG_SHIFT_UP;

            // Shift Down = F
            if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_F)) flags |= VxMountInput.FLAG_SHIFT_DOWN;

            // Horn / Special = H
            if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_H)) flags |= VxMountInput.FLAG_SPECIAL_1;

            VxMountInput currentInput = new VxMountInput(forward, right, flags);

            // 4. Send packet only if input has changed to save bandwidth
            if (!currentInput.equals(this.velthoric_lastRideInput)) {
                VxPacketHandler.sendToServer(new C2SMountInputPacket(currentInput));
                this.velthoric_lastRideInput = currentInput;
            }
        } else {
            // Reset state when not riding to ensure server clears inputs
            if (this.velthoric_lastRideInput != null && !this.velthoric_lastRideInput.equals(VxMountInput.NEUTRAL)) {
                VxPacketHandler.sendToServer(new C2SMountInputPacket(VxMountInput.NEUTRAL));
            }
            this.velthoric_lastRideInput = null;
        }
    }
}