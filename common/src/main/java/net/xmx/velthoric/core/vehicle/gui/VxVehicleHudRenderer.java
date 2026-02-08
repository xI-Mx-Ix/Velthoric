/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.vehicle.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.xmx.velthoric.core.body.client.VxClientBodyManager;
import net.xmx.velthoric.core.body.type.VxBody;
import net.xmx.velthoric.core.mounting.entity.VxMountingEntity;
import net.xmx.velthoric.core.vehicle.VxWheeledVehicle;
import net.xmx.velthoric.event.api.VxRenderEvent;

import java.util.UUID;

/**
 * Handles the rendering of vehicle information (Speed, Gear) on the player's HUD.
 *
 * @author xI-Mx-Ix
 */
public class VxVehicleHudRenderer {

    /**
     * Registers the HUD rendering event listener.
     * This should be called during client initialization.
     */
    public static void registerEvents() {
        VxRenderEvent.ClientRenderHudEvent.EVENT.register(VxVehicleHudRenderer::onRenderHud);
    }

    /**
     * Renders the speedometer and gear indicator if the player is driving a Velthoric vehicle.
     *
     * @param event The HUD render event containing graphics context.
     */
    private static void onRenderHud(VxRenderEvent.ClientRenderHudEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // The player rides a Minecraft Entity (the seat), not the VxVehicle directly
        Entity ride = mc.player.getVehicle();

        if (ride instanceof VxMountingEntity mountingEntity) {
            // Retrieve the UUID from the mounting entity (seat)
            if (mountingEntity.getPhysicsId().isEmpty()) return;
            UUID physicsId = mountingEntity.getPhysicsId().get();

            // Retrieve the actual Physics Body from the Client Manager
            VxBody body = VxClientBodyManager.getInstance().getBody(physicsId);

            // Check if the body is a Vehicle
            if (body instanceof VxWheeledVehicle vxVehicle) {
                renderVehicleInfo(event.getGuiGraphics(), mc, vxVehicle);
            }
        }
    }

    /**
     * Draws the text information onto the screen.
     */
    private static void renderVehicleInfo(GuiGraphics guiGraphics, Minecraft mc, VxWheeledVehicle vehicle) {
        // Data retrieval
        int speed = Math.round(vehicle.getSpeedKmh());
        int gear = vehicle.get(VxWheeledVehicle.SYNC_GEAR);

        // Format Gear String
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

        // Calculate Position (Bottom Right)
        int width = guiGraphics.guiWidth();
        int height = guiGraphics.guiHeight();
        int padding = 10;
        int textHeight = mc.font.lineHeight;

        int speedX = width - mc.font.width(speedString) - padding;
        int speedY = height - padding - textHeight;

        int gearX = width - mc.font.width(gearDisplay) - padding;
        int gearY = speedY - textHeight - 5; // 5px spacing

        // Render Text
        guiGraphics.drawString(mc.font, gearDisplay, gearX, gearY, 0xFFFFFF, true);
        guiGraphics.drawString(mc.font, speedString, speedX, speedY, 0xFFFFFF, true);
    }
}