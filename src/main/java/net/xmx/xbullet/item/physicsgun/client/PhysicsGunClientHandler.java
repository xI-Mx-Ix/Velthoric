package net.xmx.xbullet.item.physicsgun.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.xmx.xbullet.init.registry.ItemRegistry;
import net.xmx.xbullet.item.physicsgun.packet.C2SPhysicsGunScrollPacket;
import net.xmx.xbullet.item.physicsgun.packet.C2SRequestPhysicsGunActionPacket;
import net.xmx.xbullet.network.NetworkHandler;
import org.lwjgl.glfw.GLFW;

public class PhysicsGunClientHandler {

    private static boolean isHoldingObject = false;

    public static void setHoldingObject(boolean holding) {
        isHoldingObject = holding;
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;

        if (player == null || !player.getMainHandItem().is(ItemRegistry.PHYSICS_GUN.get())) {
            if (isHoldingObject) {
                handleRelease();
            }
            return;
        }

        if (event.getButton() == InputConstants.MOUSE_BUTTON_LEFT) {
            if (event.getAction() == GLFW.GLFW_PRESS && !isHoldingObject) {
                handleGrab();
            } else if (event.getAction() == GLFW.GLFW_RELEASE && isHoldingObject) {
                handleRelease();
            }
            if (mc.screen == null) {
                event.setCanceled(true);
            }
        }

        if (event.getButton() == InputConstants.MOUSE_BUTTON_RIGHT) {
            if (event.getAction() == GLFW.GLFW_PRESS) {
                handleFreeze();
            }
            if (mc.screen == null) {
                event.setCanceled(true);
            }
        }
    }

    private static void handleGrab() {
        NetworkHandler.CHANNEL.sendToServer(new C2SRequestPhysicsGunActionPacket(C2SRequestPhysicsGunActionPacket.ActionType.GRAB));
    }

    private static void handleRelease() {
        if (isHoldingObject) {
            isHoldingObject = false;
            NetworkHandler.CHANNEL.sendToServer(new C2SRequestPhysicsGunActionPacket(C2SRequestPhysicsGunActionPacket.ActionType.RELEASE));
        }
    }

    private static void handleFreeze() {
        NetworkHandler.CHANNEL.sendToServer(new C2SRequestPhysicsGunActionPacket(C2SRequestPhysicsGunActionPacket.ActionType.FREEZE));
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Player player = Minecraft.getInstance().player;
        if (player != null && isHoldingObject && player.getMainHandItem().is(ItemRegistry.PHYSICS_GUN.get())) {
            NetworkHandler.CHANNEL.sendToServer(new C2SPhysicsGunScrollPacket((float) event.getScrollDelta()));
            if (Minecraft.getInstance().screen == null) {
                event.setCanceled(true);
            }
        }
    }
}