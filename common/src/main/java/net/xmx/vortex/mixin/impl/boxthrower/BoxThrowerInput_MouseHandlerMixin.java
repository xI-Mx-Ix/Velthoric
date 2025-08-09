package net.xmx.vortex.mixin.impl.boxthrower;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.xmx.vortex.init.registry.ItemRegistry; 
import net.xmx.vortex.item.boxthrower.packet.BoxThrowerActionPacket;
import net.xmx.vortex.network.NetworkHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class BoxThrowerInput_MouseHandlerMixin {

    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void onBoxThrowerPress(long window, int button, int action, int mods, CallbackInfo ci) {
        var player = this.minecraft.player;

        if (player == null || this.minecraft.screen != null) {
            return;
        }

        boolean isHoldingBoxThrower = player.getMainHandItem().is(ItemRegistry.BOX_THROWER.get())
                               || player.getOffhandItem().is(ItemRegistry.BOX_THROWER.get());

        if (!isHoldingBoxThrower) {
            return;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (action == GLFW.GLFW_PRESS) {

                NetworkHandler.sendToServer(new BoxThrowerActionPacket(BoxThrowerActionPacket.ActionType.START_SHOOTING));
            } else if (action == GLFW.GLFW_RELEASE) {

                NetworkHandler.sendToServer(new BoxThrowerActionPacket(BoxThrowerActionPacket.ActionType.STOP_SHOOTING));
            }

            ci.cancel();
        }
    }
}