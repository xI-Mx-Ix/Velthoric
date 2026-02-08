/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.mounting.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.xmx.velthoric.math.VxConversions;
import net.xmx.velthoric.core.mounting.util.VxMountingRenderUtils;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects into {@link EntityRenderer} to adjust the nametag orientation
 * when an entity is mounted on a rotated physics body.
 *
 * @author xI-Mx-Ix
 */
@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer {

    /**
     * Injected before the camera orientation is applied to the nametag. This inverts the
     * physics rotation of the vehicle to ensure the nametag always faces the camera correctly.
     */
    @Inject(method = "renderNameTag", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionf;)V", ordinal = 0))
    private <T extends Entity> void velthoric_invertPhysicsRotationBeforeCameraOrientation(
            T entity, Component displayName, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, float partialTick, CallbackInfo ci) {

        float partialTicks = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        VxMountingRenderUtils.INSTANCE.ifMountedOnBody(entity, partialTicks, physQuat -> {
            Quaternionf physRotation = VxConversions.toJoml(physQuat, new Quaternionf());
            physRotation.conjugate(); // Invert the rotation
            poseStack.mulPose(physRotation);
        });
    }
}