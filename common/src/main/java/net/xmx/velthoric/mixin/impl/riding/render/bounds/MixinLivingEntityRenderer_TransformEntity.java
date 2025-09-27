/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.riding.render.bounds;

import com.github.stephengold.joltjni.Quat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.xmx.velthoric.physics.object.client.VxClientObjectDataStore;
import net.xmx.velthoric.physics.object.client.VxClientObjectInterpolator;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.riding.VxRidingProxyEntity;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Overrides the model rotation setup for any {@link LivingEntity} that is a passenger
 * of a physics-driven vehicle. This ensures the entity's model correctly aligns
 * with the vehicle's orientation.
 *
 * <p>By injecting into {@code setupRotations}, this mixin takes full control over the
 * model's orientation, while allowing the name tag (rendered in the superclass)
 * to remain correctly billboarded towards the camera.</p>
 *
 * @author xI-Mx-Ix
 */
@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer_TransformEntity {

    @Unique
    private static final Quat velthoric_interpolatedRotation_ler = new Quat();

    /**
     * Injects at the head of {@code setupRotations} to replace vanilla rotation logic
     * with our vehicle-based transformations. It is cancelled to prevent vanilla
     * rotations (like yaw based on head movement) from interfering.
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
        if (!(entity.getVehicle() instanceof VxRidingProxyEntity proxy)) {
            // Not riding our vehicle, let vanilla logic run.
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

            // Note: We only need rotation here. The position is already handled by
            // MixinGameRenderer_SmoothEntityPosition, which adjusts the entity's
            // render coordinates before this renderer is even called.
            interpolator.interpolateRotation(store, index, partialTicks, velthoric_interpolatedRotation_ler);

            Quaternionf vehicleRotation = new Quaternionf(
                    velthoric_interpolatedRotation_ler.getX(),
                    velthoric_interpolatedRotation_ler.getY(),
                    velthoric_interpolatedRotation_ler.getZ(),
                    velthoric_interpolatedRotation_ler.getW()
            );

            // Apply the vehicle's rotation to the model's PoseStack.
            poseStack.mulPose(vehicleRotation);

            // Re-apply essential vanilla rotation logic that happens *after* the main transform.
            // This makes the entity model face the correct direction from the player's perspective.
            applyDefaultRotations(entity, poseStack, ageInTicks, rotationYaw, partialTicks);

            // Cancel the original method to prevent it from applying conflicting rotations.
            ci.cancel();
        });
    }

    /**
     * Replicates the necessary vanilla rotation logic that should apply on top of our
     * physics-based rotation, such as the death animation or the player's body yaw.
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
            // which is essential for a correct appearance when riding.
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - rotationYaw));
        }
    }
}