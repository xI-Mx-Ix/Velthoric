package net.xmx.xbullet.mixin.riding;

import com.github.stephengold.joltjni.RMat44;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.xmx.xbullet.math.MatrixUtil;
import net.xmx.xbullet.physics.object.riding.RidingProxyEntity;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin extends EntityRenderer<LivingEntity> {

    protected LivingEntityRendererMixin(EntityRendererProvider.Context pContext) {
        super(pContext);
    }

    @Inject(
            method = "setupRotations(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void xbullet_applyPhysicsObjectTransform(LivingEntity entity, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTicks, CallbackInfo ci) {
        if (entity.getVehicle() instanceof RidingProxyEntity proxy) {
            proxy.getInterpolatedTransform().ifPresent(physicsTransform -> {
                ci.cancel();

                poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));

                RMat44 joltMatrix = physicsTransform.toRMat44();
                Matrix4f jomlMatrix = MatrixUtil.convert(joltMatrix);
                poseStack.last().pose().mul(jomlMatrix);

                poseStack.scale(-1.0F, -1.0F, 1.0F);

                if (LivingEntityRenderer.isEntityUpsideDown(entity)) {
                    poseStack.translate(0.0F, entity.getBbHeight() + 0.1F, 0.0F);
                    poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
                }
            });
        }
    }
}