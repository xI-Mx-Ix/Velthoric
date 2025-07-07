package net.xmx.xbullet.item.physicsgun.event;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.xmx.xbullet.init.registry.ItemRegistry;
import net.xmx.xbullet.item.physicsgun.packet.PhysicsGunActionPacket;
import net.xmx.xbullet.network.NetworkHandler;
import org.lwjgl.glfw.GLFW;

public class PhysicsGunClientEvents {

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) return;

        boolean isHoldingGun = player.getMainHandItem().is(ItemRegistry.PHYSICS_GUN.get())
                || player.getOffhandItem().is(ItemRegistry.PHYSICS_GUN.get());

        if (!isHoldingGun) {

            return;
        }

        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {

            if (event.getAction() == GLFW.GLFW_PRESS) {

                NetworkHandler.CHANNEL.sendToServer(new PhysicsGunActionPacket(PhysicsGunActionPacket.ActionType.START_GRAB_ATTEMPT));
            } else if (event.getAction() == GLFW.GLFW_RELEASE) {

                NetworkHandler.CHANNEL.sendToServer(new PhysicsGunActionPacket(PhysicsGunActionPacket.ActionType.STOP_GRAB_ATTEMPT));
            }
        }

        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (event.getAction() == GLFW.GLFW_PRESS) {

                NetworkHandler.CHANNEL.sendToServer(new PhysicsGunActionPacket(PhysicsGunActionPacket.ActionType.STOP_GRAB_ATTEMPT));
                NetworkHandler.CHANNEL.sendToServer(new PhysicsGunActionPacket(PhysicsGunActionPacket.ActionType.FREEZE_OBJECT));
            }
        }

        if (mc.screen == null && event.getButton() != GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            if (isHoldingGun) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) return;

        boolean isHoldingGun = player.getMainHandItem().is(ItemRegistry.PHYSICS_GUN.get())
                || player.getOffhandItem().is(ItemRegistry.PHYSICS_GUN.get());

        if (isHoldingGun) {
            NetworkHandler.CHANNEL.sendToServer(new PhysicsGunActionPacket((float) event.getScrollDelta()));
            event.setCanceled(true);
        }
    }
}