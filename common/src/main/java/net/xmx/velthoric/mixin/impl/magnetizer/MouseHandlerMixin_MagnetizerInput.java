/*
This file is part of Velthoric.
Licensed under LGPL 3.0.
*/
package net.xmx.velthoric.mixin.impl.magnetizer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.xmx.velthoric.init.registry.ItemRegistry;
import net.xmx.velthoric.item.magnetizer.packet.MagnetizerActionPacket;
import net.xmx.velthoric.network.NetworkHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin_MagnetizerInput {

    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void onMagnetizerPress(long window, int button, int action, int mods, CallbackInfo ci) {
        var player = this.minecraft.player;
        if (player == null) return;
        if (minecraft.screen != null) return;

        boolean isHoldingMagnetizer = player.getMainHandItem().is(ItemRegistry.MAGNETIZER.get())
                || player.getOffhandItem().is(ItemRegistry.MAGNETIZER.get());

        if (!isHoldingMagnetizer) {
            return;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (action == GLFW.GLFW_PRESS) {
                NetworkHandler.sendToServer(new MagnetizerActionPacket(MagnetizerActionPacket.ActionType.START_ATTRACT));
            } else if (action == GLFW.GLFW_RELEASE) {
                NetworkHandler.sendToServer(new MagnetizerActionPacket(MagnetizerActionPacket.ActionType.STOP_ACTION));
            }
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (action == GLFW.GLFW_PRESS) {
                NetworkHandler.sendToServer(new MagnetizerActionPacket(MagnetizerActionPacket.ActionType.START_REPEL));
            } else if (action == GLFW.GLFW_RELEASE) {
                NetworkHandler.sendToServer(new MagnetizerActionPacket(MagnetizerActionPacket.ActionType.STOP_ACTION));
            }
        }

        if (this.minecraft.screen == null && button != GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            ci.cancel();
        }
    }
}