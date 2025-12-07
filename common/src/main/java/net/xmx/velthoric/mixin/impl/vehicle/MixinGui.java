/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.vehicle;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.xmx.velthoric.physics.body.client.VxClientBodyManager;
import net.xmx.velthoric.physics.body.type.VxBody;
import net.xmx.velthoric.physics.mounting.entity.VxMountingEntity;
import net.xmx.velthoric.physics.vehicle.VxVehicle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Mixin to render the vehicle HUD (Speedometer and Gear) when the player is driving.
 *
 * @author xI-Mx-Ix
 */
@Mixin(Gui.class)
public class MixinGui {

    @Inject(method = "render", at = @At("TAIL"))
    private void velthoric_renderVehicleHud(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // The player rides a Minecraft Entity (the seat), not the VxVehicle directly
        Entity ride = mc.player.getVehicle();

        if (ride instanceof VxMountingEntity mountingEntity) {

            // 1. Get the Physics Body UUID from the mounting entity
            UUID physicsId = mountingEntity.getPhysicsId().get();

            // 2. Retrieve the actual Physics Body from the Client Manager
            VxBody body = VxClientBodyManager.getInstance().getBody(physicsId);

            // 3. Check if the body is a Vehicle
            if (body instanceof VxVehicle vxVehicle) {

                // 4. Get Data
                int speed = Math.round(vxVehicle.getSpeedKmh());
                int gear = vxVehicle.getTransmission().getGear();

                // 5. Format Gear String
                String gearString;
                if (gear == 0) {
                    gearString = "N";
                } else if (gear == -1) {
                    gearString = "R";
                } else {
                    gearString = String.valueOf(gear);
                }

                String speedString = speed + " km/h";
                String gearDisplay = "Gear: " + gearString;

                // 6. Calculate Position (Bottom Right)
                int width = guiGraphics.guiWidth();
                int height = guiGraphics.guiHeight();

                int padding = 10;
                int textHeight = mc.font.lineHeight;

                int speedX = width - mc.font.width(speedString) - padding;
                int speedY = height - padding - textHeight;

                int gearX = width - mc.font.width(gearDisplay) - padding;
                int gearY = speedY - textHeight - 5; // 5px spacing

                // 7. Render Text
                guiGraphics.drawString(mc.font, gearDisplay, gearX, gearY, 0xFFFFFF, true);
                guiGraphics.drawString(mc.font, speedString, speedX, speedY, 0xFFFFFF, true);
            }
        }
    }
}