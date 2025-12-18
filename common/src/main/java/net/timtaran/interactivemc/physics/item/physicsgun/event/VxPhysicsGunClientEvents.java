/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.item.physicsgun.event;

import dev.architectury.event.EventResult;
import net.minecraft.client.Minecraft;
import net.timtaran.interactivemc.physics.event.api.VxKeyEvent;
import net.timtaran.interactivemc.physics.init.registry.ItemRegistry;
import net.timtaran.interactivemc.physics.item.physicsgun.manager.VxPhysicsGunClientManager;
import net.timtaran.interactivemc.physics.item.physicsgun.packet.VxPhysicsGunActionPacket;
import net.timtaran.interactivemc.physics.network.VxPacketHandler;
import org.lwjgl.glfw.GLFW;

/**
 * Handles client-side keyboard input events for the Physics Gun.
 * Mouse input is handled via a dedicated Mixin for better control over rotation.
 *
 * @author xI-Mx-Ix
 */
public class VxPhysicsGunClientEvents {

    /**
     * Registers all necessary client-side event listeners for the Physics Gun.
     */
    public static void registerEvents() {
        VxKeyEvent.EVENT.register(VxPhysicsGunClientEvents::onKeyPress);
    }

    private static EventResult onKeyPress(VxKeyEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null) {
            return EventResult.pass();
        }

        var player = minecraft.player;
        if (player == null) {
            return EventResult.pass();
        }

        boolean isHoldingGun = player.getMainHandItem().is(ItemRegistry.PHYSICS_GUN.get())
                            || player.getOffhandItem().is(ItemRegistry.PHYSICS_GUN.get());
        if (!isHoldingGun) {
            return EventResult.pass();
        }

        var clientManager = VxPhysicsGunClientManager.getInstance();

        if (clientManager.isGrabbing(player) && event.getKey() == GLFW.GLFW_KEY_E) {
            if (event.getAction() == GLFW.GLFW_PRESS) {
                if (!clientManager.isRotationMode()) {
                    clientManager.setRotationMode(true);
                    VxPacketHandler.sendToServer(new VxPhysicsGunActionPacket(VxPhysicsGunActionPacket.ActionType.START_ROTATION_MODE));
                }
            } else if (event.getAction() == GLFW.GLFW_RELEASE) {
                if (clientManager.isRotationMode()) {
                    clientManager.setRotationMode(false);
                    VxPacketHandler.sendToServer(new VxPhysicsGunActionPacket(VxPhysicsGunActionPacket.ActionType.STOP_ROTATION_MODE));
                }
            }
            return EventResult.interruptFalse();
        }
        return EventResult.pass();
    }
}