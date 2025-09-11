/*
This file is part of Velthoric.
Licensed under LGPL 3.0.
*/
package net.xmx.velthoric.mixin.impl.physicsgun;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.xmx.velthoric.init.registry.ItemRegistry;
import net.xmx.velthoric.item.physicsgun.manager.PhysicsGunClientManager;
import net.xmx.velthoric.item.physicsgun.packet.PhysicsGunActionPacket;
import net.xmx.velthoric.network.NetworkHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin_PhysicsGunHandling {

    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void onTurnPlayer(CallbackInfo ci) {
        var clientManager = PhysicsGunClientManager.getInstance();
        if (clientManager.isRotationMode()) {
            if (this.accumulatedDX != 0.0D || this.accumulatedDY != 0.0D) {
                NetworkHandler.sendToServer(new PhysicsGunActionPacket((float) this.accumulatedDX, (float) this.accumulatedDY));
            }
            this.accumulatedDX = 0.0D;
            this.accumulatedDY = 0.0D;
            ci.cancel();
        }
    }

    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void onMouseButtonPress(long window, int button, int action, int mods, CallbackInfo ci) {
        var player = this.minecraft.player;
        if (player == null || this.minecraft.screen != null) return;

        var clientManager = PhysicsGunClientManager.getInstance();
        boolean isHoldingGun = player.getMainHandItem().is(ItemRegistry.PHYSICS_GUN.get())
                || player.getOffhandItem().is(ItemRegistry.PHYSICS_GUN.get());

        if (!isHoldingGun) {
            if (clientManager.isTryingToGrab(player)) {
                clientManager.stopGrabAttempt();
            }
            return;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (action == GLFW.GLFW_PRESS) {
                clientManager.startGrabAttempt();
            } else if (action == GLFW.GLFW_RELEASE) {
                clientManager.stopGrabAttempt();
            }
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (action == GLFW.GLFW_PRESS) {
                NetworkHandler.sendToServer(new PhysicsGunActionPacket(PhysicsGunActionPacket.ActionType.FREEZE_OBJECT));
            }
        }

        if (this.minecraft.screen == null && button != GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            if (isHoldingGun) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        var player = this.minecraft.player;
        if (player == null || this.minecraft.screen != null) return;

        boolean isHoldingGun = player.getMainHandItem().is(ItemRegistry.PHYSICS_GUN.get())
                || player.getOffhandItem().is(ItemRegistry.PHYSICS_GUN.get());
        if (!isHoldingGun) return;

        boolean isTryingToGrab = GLFW.glfwGetMouseButton(this.minecraft.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        if (isTryingToGrab) {
            NetworkHandler.sendToServer(new PhysicsGunActionPacket((float) vertical));
            ci.cancel();
        }
    }
}