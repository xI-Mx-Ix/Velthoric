package net.xmx.vortex.mixin.impl.raytracing;

import com.github.stephengold.joltjni.Vec3;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import net.xmx.vortex.network.NetworkHandler;
import net.xmx.vortex.physics.object.raycast.packet.PhysicsClickPacket;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class ClickNotifier_MouseHandlerMixin {

    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "onPress", at = @At("HEAD"))
    private void vortex_onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
        LocalPlayer player = this.minecraft.player;

        if (player == null || this.minecraft.level == null || this.minecraft.screen != null) {
            return;
        }

        if (action == InputConstants.PRESS) {
            boolean isRightClick = button == InputConstants.MOUSE_BUTTON_RIGHT;
            boolean isLeftClick = button == InputConstants.MOUSE_BUTTON_LEFT;

            if (!isRightClick && !isLeftClick) {
                return;
            }

            PhysicsClickPacket packet = vortex$createClickPacket(player, isRightClick);
            NetworkHandler.sendToServer(packet);
        }
    }

    @Unique
    @NotNull
    private static PhysicsClickPacket vortex$createClickPacket(LocalPlayer player, boolean isRightClick) {
        net.minecraft.world.phys.Vec3 startVec = player.getEyePosition(1.0F);
        net.minecraft.world.phys.Vec3 lookVec = player.getLookAngle();

        Vec3 rayDirection = new Vec3((float)lookVec.x, (float)lookVec.y, (float)lookVec.z);
        rayDirection.normalizeInPlace();

        return new PhysicsClickPacket(
                (float) startVec.x, (float) startVec.y, (float) startVec.z,
                rayDirection.getX(), rayDirection.getY(), rayDirection.getZ(),
                isRightClick
        );
    }
}