package net.xmx.xbullet.item.magnetizer.event;

import net.minecraft.client.Minecraft;
import net.xmx.xbullet.init.registry.ItemRegistry;
import net.xmx.xbullet.item.magnetizer.packet.MagnetizerActionPacket;
import net.xmx.xbullet.network.NetworkHandler;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

public class MagnetizerClientEvents {

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) return;

        boolean isHoldingMagnetizer = player.getMainHandItem().is(ItemRegistry.MAGNETIZER.get())
                || player.getOffhandItem().is(ItemRegistry.MAGNETIZER.get());

        if (!isHoldingMagnetizer) {
            return;
        }

        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (event.getAction() == GLFW.GLFW_PRESS) {
                NetworkHandler.CHANNEL.sendToServer(new MagnetizerActionPacket(MagnetizerActionPacket.ActionType.START_ATTRACT));
            } else if (event.getAction() == GLFW.GLFW_RELEASE) {
                NetworkHandler.CHANNEL.sendToServer(new MagnetizerActionPacket(MagnetizerActionPacket.ActionType.STOP_ACTION));
            }
        }

        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (event.getAction() == GLFW.GLFW_PRESS) {
                NetworkHandler.CHANNEL.sendToServer(new MagnetizerActionPacket(MagnetizerActionPacket.ActionType.START_REPEL));
            } else if (event.getAction() == GLFW.GLFW_RELEASE) {
                NetworkHandler.CHANNEL.sendToServer(new MagnetizerActionPacket(MagnetizerActionPacket.ActionType.STOP_ACTION));
            }
        }

        if (mc.screen == null && event.getButton() != GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            event.setCanceled(true);
        }
    }
}