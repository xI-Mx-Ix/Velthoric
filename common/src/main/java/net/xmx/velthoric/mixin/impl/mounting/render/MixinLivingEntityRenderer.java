/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.mounting.render;

import com.github.stephengold.joltjni.Quat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.xmx.velthoric.physics.mounting.entity.VxMountingEntity;
import net.xmx.velthoric.physics.object.client.VxClientObjectDataStore;
import net.xmx.velthoric.physics.object.client.VxClientObjectInterpolator;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Overrides the model rotation setup for any {@link LivingEntity} that is a passenger
 * of a physics-driven vehicle. This ensures the entity's model correctly aligns
 * with the vehicle's orientation, while allowing vanilla logic like death animations
 * to be layered on top.
 *
 * @author xI-Mx-Ix
 */
@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer {

    /**
     * A reusable Quat to store interpolated rotation data, avoiding re-allocation each frame.
     */
    @Unique
    private static final Quat velthoric_interpolatedRotation = new Quat();

    /**
     * Injects into {@code setupRotations} to replace vanilla rotation logic with vehicle-based
     * transformations. It cancels the original method to prevent vanilla rotations (like yaw based
     * on head movement) from interfering.
     *
     * @param entity The living entity being rendered.
     * @param poseStack The current PoseStack.
     * @param ageInTicks The entity's age in ticks.
     * @param rotationYaw The entity's body yaw.
     * @param partialTicks The fraction of a tick for interpolation.
     * @param ci The callback info, used to cancel the original method.
     */
    @Inject(method = "setupRotations", at = @At("HEAD"), cancellable = true)
    private void velthoric_applyVehicleRotations(LivingEntity entity, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTicks, CallbackInfo ci) {
        if (!(entity.getVehicle() instanceof VxMountingEntity proxy)) {
            return;
        }

        proxy.getPhysicsObjectId().ifPresent(id -> {
            VxClientObjectManager manager = VxClientObjectManager.getInstance();
            VxClientObjectDataStore store = manager.getStore();
            VxClientObjectInterpolator interpolator = manager.getInterpolator();
            Integer index = store.getIndexForId(id);

            if (index == null || !store.render_isInitialized[index]) {
                return;
            }

            interpolator.interpolateRotation(store, index, partialTicks, velthoric_interpolatedRotation);

            Quaternionf vehicleRotation = new Quaternionf(
                    velthoric_interpolatedRotation.getX(),
                    velthoric_interpolatedRotation.getY(),
                    velthoric_interpolatedRotation.getZ(),
                    velthoric_interpolatedRotation.getW()
            );

            // Apply the vehicle's base rotation to the model's PoseStack.
            poseStack.mulPose(vehicleRotation);

            // Re-apply essential vanilla rotation logic that should occur after the main transform.
            applyDefaultRotations(entity, poseStack, ageInTicks, rotationYaw, partialTicks);

            // Cancel the original method to prevent it from applying conflicting rotations.
            ci.cancel();
        });
    }

    /**
     * Replicates the necessary vanilla rotation logic that should apply on top of the
     * physics-based rotation, such as the death animation or the entity's body yaw.
     *
     * @param entity The living entity being rendered.
     * @param poseStack The current PoseStack.
     * @param ageInTicks The entity's age in ticks.
     * @param rotationYaw The entity's body yaw.
     * @param partialTicks The fraction of a tick for interpolation.
     */
    @Unique
    private void applyDefaultRotations(LivingEntity entity, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTicks) {
        if (entity.deathTime > 0) {
            float f = ((float)entity.deathTime + partialTicks - 1.0F) / 20.0F * 1.6F;
            f = Mth.sqrt(f);
            if (f > 1.0F) {
                f = 1.0F;
            }
            poseStack.mulPose(Axis.ZP.rotationDegrees(f * 90.0f));
        } else if (entity.isAutoSpinAttack()) {
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F - entity.getXRot()));
            poseStack.mulPose(Axis.YP.rotationDegrees(((float)entity.tickCount + partialTicks) * -75.0F));
        } else {
            // This rotates the model to face forward relative to the player's view,
            // which is essential for a correct appearance when mounted.
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - rotationYaw));
        }
    }
}