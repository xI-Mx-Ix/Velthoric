package net.xmx.vortex.mixin.impl.riding;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.xmx.vortex.physics.object.riding.RidingProxyEntity;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public class PlayerRendererMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void onRender(AbstractClientPlayer entity, float entityYaw, float partialTicks, PoseStack poseStack,
                          net.minecraft.client.renderer.MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (entity.getVehicle() instanceof RidingProxyEntity proxy) {

            Quaternionf rotation = proxy.getPhysicsObjectRotation();
            poseStack.mulPose(rotation);
        }
    }
}