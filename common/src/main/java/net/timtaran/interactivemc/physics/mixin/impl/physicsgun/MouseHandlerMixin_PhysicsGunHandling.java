/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.mixin.impl.physicsgun;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.timtaran.interactivemc.physics.init.registry.ItemRegistry;
import net.timtaran.interactivemc.physics.item.physicsgun.manager.VxPhysicsGunClientManager;
import net.timtaran.interactivemc.physics.item.physicsgun.packet.VxPhysicsGunActionPacket;
import net.timtaran.interactivemc.physics.network.VxPacketHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to handle mouse input specifically for the Physics Gun.
 * It is given a higher priority to ensure it runs before the generic mouse event system.
 *
 * @author xI-Mx-Ix
 */
@Mixin(value = MouseHandler.class, priority = 1100)
public class MouseHandlerMixin_PhysicsGunHandling {

    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;
    @Shadow @Final private Minecraft minecraft;

    /**
     * Injects into the player turning logic to handle object rotation
     * when the physics gun is in rotation mode.
     * This cancels the normal player view rotation and instead sends the mouse delta to the server.
     */
    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void onTurnPlayer(CallbackInfo ci) {
        var clientManager = VxPhysicsGunClientManager.getInstance();
        if (clientManager.isRotationMode()) {
            if (this.accumulatedDX != 0.0D || this.accumulatedDY != 0.0D) {
                VxPacketHandler.sendToServer(new VxPhysicsGunActionPacket((float) this.accumulatedDX, (float) this.accumulatedDY));
            }
            // Reset accumulated values to prevent residual movement.
            this.accumulatedDX = 0.0D;
            this.accumulatedDY = 0.0D;
            ci.cancel();
        }
    }

    /**
     * Intercepts mouse button presses to control the physics gun's primary and secondary actions.
     * This logic is carefully structured to avoid interfering with GUI screens and to handle
     * state cleanup correctly when switching items or opening a GUI.
     */
    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void onMouseButtonPress(long window, int button, int action, int mods, CallbackInfo ci) {
        var player = this.minecraft.player;
        if (player == null) {
            return;
        }

        var clientManager = VxPhysicsGunClientManager.getInstance();
        boolean isHoldingGun = player.getMainHandItem().is(ItemRegistry.PHYSICS_GUN.get())
                || player.getOffhandItem().is(ItemRegistry.PHYSICS_GUN.get());

        // If a GUI is open, the physics gun should not interfere with mouse clicks.
        // The event should be passed through to be handled by the GUI.
        // It also ensures that any grabbing action is stopped if a GUI is opened during it.
        if (this.minecraft.screen != null) {
            if (isHoldingGun && clientManager.isTryingToGrab(player)) {
                clientManager.stopGrabAttempt();
            }
            return;
        }

        // If the player is not holding the gun, ensure any active grab attempt is stopped.
        // This handles cases where the player switches away from the gun while using it.
        if (!isHoldingGun) {
            if (clientManager.isTryingToGrab(player)) {
                clientManager.stopGrabAttempt();
            }
            return;
        }

        // At this point, the player is holding the gun and is not in a GUI.
        boolean eventHandled = false;

        // Handle left-click for grabbing objects.
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (action == GLFW.GLFW_PRESS) {
                clientManager.startGrabAttempt();
            } else if (action == GLFW.GLFW_RELEASE) {
                clientManager.stopGrabAttempt();
            }
            eventHandled = true;
        }

        // Handle right-click for freezing objects.
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (action == GLFW.GLFW_PRESS) {
                VxPacketHandler.sendToServer(new VxPhysicsGunActionPacket(VxPhysicsGunActionPacket.ActionType.FREEZE_OBJECT));
            }
            eventHandled = true;
        }

        // If the left or right mouse button was handled, cancel the event to prevent
        // default actions like breaking blocks or using items.
        if (eventHandled) {
            ci.cancel();
        }
    }

    /**
     * Intercepts mouse scrolling to adjust the distance of a grabbed object.
     * This only occurs if the player is holding the gun and the primary action
     * button (left-click) is held down.
     */
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        var player = this.minecraft.player;
        if (player == null || this.minecraft.screen != null) return;

        boolean isHoldingGun = player.getMainHandItem().is(ItemRegistry.PHYSICS_GUN.get())
                || player.getOffhandItem().is(ItemRegistry.PHYSICS_GUN.get());
        if (!isHoldingGun) return;

        boolean isGrabbing = GLFW.glfwGetMouseButton(this.minecraft.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        if (isGrabbing) {
            VxPacketHandler.sendToServer(new VxPhysicsGunActionPacket((float) vertical));
            ci.cancel();
        }
    }
}