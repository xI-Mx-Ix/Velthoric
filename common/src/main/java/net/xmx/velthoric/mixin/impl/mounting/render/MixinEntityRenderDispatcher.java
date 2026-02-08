/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.mounting.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.math.VxConversions;
import net.xmx.velthoric.core.mounting.entity.VxMountingEntity;
import net.xmx.velthoric.core.mounting.util.VxMountingRenderUtils;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Modifies the EntityRenderDispatcher to correctly render entities
 * that are mounted on a physics-driven body.
 *
 * @author xI-Mx-Ix
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class MixinEntityRenderDispatcher {

    /**
     * Injects before the main entity rendering call to apply the physics-based vehicle
     * rotation to the PoseStack.
     */
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", shift = At.Shift.BEFORE))
    private <E extends Entity> void velthoric_applyFullEntityTransform(E entity, double x, double y, double z, float rotationYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        VxMountingRenderUtils.INSTANCE.ifMountedOnBody(entity, partialTicks, physQuat -> {
            Quaternionf physRotation = VxConversions.toJoml(physQuat, new Quaternionf());
            poseStack.mulPose(physRotation);
        });
    }

    /**
     * Redirects the call to {@code entity.getViewVector(partialTicks)} when rendering a hitbox
     * to correctly transform the view vector into the vehicle's local space before rendering.
     */
    @Redirect(method = "renderHitbox", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewVector(F)Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 velthoric_redirectViewVectorForHitbox(Entity instance, float partialTicks) {
        Vec3 originalViewVector = instance.getViewVector(partialTicks);

        if (instance.getVehicle() instanceof VxMountingEntity proxy) {
            return VxMountingRenderUtils.INSTANCE.getInterpolatedRotation(proxy, partialTicks)
                    .map(physQuat -> {
                        Quaterniond physRotation = new Quaterniond(physQuat.getX(), physQuat.getY(), physQuat.getZ(), physQuat.getW());
                        physRotation.invert(); // From world-space back to local-space

                        Vector3d correctedVector = VxConversions.toJoml(originalViewVector, new Vector3d());
                        physRotation.transform(correctedVector);

                        return VxConversions.toMinecraft(correctedVector);
                    })
                    .orElse(originalViewVector);
        }
        return originalViewVector;
    }
}