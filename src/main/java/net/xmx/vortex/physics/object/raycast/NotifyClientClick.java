package net.xmx.vortex.physics.object.raycast;

import com.github.stephengold.joltjni.Vec3;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.xmx.vortex.network.NetworkHandler;
import net.xmx.vortex.physics.object.raycast.packet.PhysicsClickPacket;
import org.jetbrains.annotations.NotNull;

public class NotifyClientClick {

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null || mc.level == null || mc.screen != null) {
            return;
        }

        if (event.getAction() == InputConstants.PRESS) {
            boolean isRightClick = event.getButton() == InputConstants.MOUSE_BUTTON_RIGHT;
            boolean isLeftClick = event.getButton() == InputConstants.MOUSE_BUTTON_LEFT;

            if (!isRightClick && !isLeftClick) {
                return;
            }

            PhysicsClickPacket packet = createClickPacket(player, isRightClick);
            NetworkHandler.CHANNEL.sendToServer(packet);
        }
    }

    @NotNull
    private static PhysicsClickPacket createClickPacket(LocalPlayer player, boolean isRightClick) {
        net.minecraft.world.phys.Vec3 startVec = player.getEyePosition(1.0F);
        net.minecraft.world.phys.Vec3 lookVec = player.getLookAngle();

        Vec3 rayDirection = new Vec3(lookVec.x, lookVec.y, lookVec.z);
        rayDirection.normalizeInPlace();

        return new PhysicsClickPacket(
                (float) startVec.x, (float) startVec.y, (float) startVec.z,
                rayDirection.getX(), rayDirection.getY(), rayDirection.getZ(),
                isRightClick
        );
    }
}