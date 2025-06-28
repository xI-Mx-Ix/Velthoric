package net.xmx.xbullet.physics.object.global.click;

import com.github.stephengold.joltjni.Vec3; // Geändert
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.xmx.xbullet.network.NetworkHandler;
import org.jetbrains.annotations.NotNull;

public class ClientSendClick {

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null || mc.level == null) {
            return;
        }

        if (event.getAction() == InputConstants.PRESS) {

            boolean isRightClick = event.getButton() == InputConstants.MOUSE_BUTTON_RIGHT;
            boolean isLeftClick = event.getButton() == InputConstants.MOUSE_BUTTON_LEFT;

            if (!isRightClick && !isLeftClick || mc.screen != null) {
                return;
            }

            PhysicsClickPacket packet = getPhysicsClickPacket(player, isRightClick);
            NetworkHandler.CHANNEL.sendToServer(packet);
        }
    }

    @NotNull
    private static PhysicsClickPacket getPhysicsClickPacket(LocalPlayer player, boolean isRightClick) {
        net.minecraft.world.phys.Vec3 startVec = player.getEyePosition(1.0F);
        net.minecraft.world.phys.Vec3 lookVec = player.getLookAngle();
        Vec3 rayDirection = new Vec3(lookVec.x, lookVec.y, lookVec.z);
        rayDirection.normalizeInPlace();

        PhysicsClickPacket packet = new PhysicsClickPacket(
                (float) startVec.x, (float) startVec.y, (float) startVec.z,
                rayDirection.getX(), rayDirection.getY(), rayDirection.getZ(),
                isRightClick
        );
        return packet;
    }
}