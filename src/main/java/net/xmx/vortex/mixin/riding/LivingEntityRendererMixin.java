package net.xmx.vortex.mixin.riding;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.xmx.vortex.physics.object.riding.RidingProxyEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
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
    private void vortex_applyPhysicsObjectTransform(LivingEntity entity, PoseStack poseStack, float ageInTicks, float bodyYaw, float partialTicks, CallbackInfo ci) {

        if (entity.getVehicle() instanceof RidingProxyEntity proxy) {
            proxy.getInterpolatedTransform().ifPresent(physicsTransform -> {

                ci.cancel();

                float interpolatedBodyYaw = Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - interpolatedBodyYaw));

                com.github.stephengold.joltjni.Quat joltQuat = physicsTransform.getRotation();
                Quaternionf vehicleWorldRotation = new Quaternionf(joltQuat.getX(), joltQuat.getY(), joltQuat.getZ(), joltQuat.getW());

                Vector3f worldUp = new Vector3f(0.0f, 1.0f, 0.0f);
                Vector3f vehicleUp = vehicleWorldRotation.transform(new Vector3f(0.0f, 1.0f, 0.0f));
                Quaternionf vehicleTilt = new Quaternionf().rotationTo(worldUp, vehicleUp);

                vehicleTilt.conjugate();

                poseStack.mulPose(vehicleTilt);

                if (LivingEntityRenderer.isEntityUpsideDown(entity)) {
                    poseStack.translate(0.0F, entity.getBbHeight() + 0.1F, 0.0F);
                    poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
                }
            });
        }
    }
}