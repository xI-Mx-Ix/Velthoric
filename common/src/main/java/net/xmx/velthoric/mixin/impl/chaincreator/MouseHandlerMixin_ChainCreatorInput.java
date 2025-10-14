/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.chaincreator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.xmx.velthoric.init.registry.ItemRegistry;
import net.xmx.velthoric.item.chaincreator.packet.VxChainCreatorActionPacket;
import net.xmx.velthoric.network.VxPacketHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to handle mouse input for the VxChainCreatorItem.
 *
 * @author xI-Mx-Ix
 */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin_ChainCreatorInput {

    @Shadow @Final private Minecraft minecraft;

    /**
     * Injects into the mouse press handler to detect when the player uses the Chain Creator.
     */
    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void onChainCreatorPress(long window, int button, int action, int mods, CallbackInfo ci) {
        var player = this.minecraft.player;

        if (player == null || this.minecraft.screen != null) {
            return;
        }

        boolean isHoldingChainCreator = player.getMainHandItem().is(ItemRegistry.CHAIN_CREATOR.get())
                               || player.getOffhandItem().is(ItemRegistry.CHAIN_CREATOR.get());

        if (!isHoldingChainCreator) {
            return;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (action == GLFW.GLFW_PRESS) {
                VxPacketHandler.sendToServer(new VxChainCreatorActionPacket(VxChainCreatorActionPacket.ActionType.START_CREATION));
            } else if (action == GLFW.GLFW_RELEASE) {
                VxPacketHandler.sendToServer(new VxChainCreatorActionPacket(VxChainCreatorActionPacket.ActionType.FINISH_CREATION));
            }
            ci.cancel();
        }
    }
}