/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.mounting.render;

import com.github.stephengold.joltjni.Quat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.physics.mounting.entity.VxMountingEntity;
import net.xmx.velthoric.physics.body.client.VxClientBodyManager;
import net.xmx.velthoric.physics.body.client.VxClientBodyDataStore;
import net.xmx.velthoric.physics.body.client.VxClientBodyInterpolator;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Modifies the EntityRenderDispatcher to correctly render entities
 * that are mounted on a physics-driven body. This includes transforming
 * the entity itself, its name tag, and its debug hitbox to match the vehicle's orientation.
 *
 * @author xI-Mx-Ix
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class MixinEntityRenderDispatcher {

    /**
     * A temporary quaternion to store interpolated rotation data, avoiding re-allocation each frame.
     */
    @Unique
    private static final Quat velthoric_tempRenderRot = new Quat();

    /**
     * Injects before the main entity rendering call to apply the physics-based vehicle
     * rotation to the PoseStack. This transformation affects the entity's model, nametag,
     * and any other attached elements, ensuring they are all correctly oriented with the vehicle.
     * The vanilla entity rotation (yaw) is then applied on top of this vehicle transformation.
     *
     * @param entity        The entity being rendered.
     * @param partialTicks  The fraction of a tick for interpolation.
     * @param poseStack     The current PoseStack for rendering transformations.
     * @param ci            Callback info provided by Mixin.
     */
    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
                    shift = At.Shift.BEFORE
            )
    )
    private <E extends Entity> void velthoric_applyFullEntityTransform(E entity, double x, double y, double z, float rotationYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (entity.getVehicle() instanceof VxMountingEntity proxy) {
            proxy.getPhysicsBodyId().ifPresent(id -> {
                VxClientBodyManager manager = VxClientBodyManager.getInstance();
                VxClientBodyDataStore store = manager.getStore();
                VxClientBodyInterpolator interpolator = manager.getInterpolator();
                Integer index = store.getIndexForId(id);

                if (index == null || !store.render_isInitialized[index]) {
                    return;
                }

                interpolator.interpolateRotation(store, index, partialTicks, velthoric_tempRenderRot);

                Quaternionf physRotation = new Quaternionf(
                        velthoric_tempRenderRot.getX(),
                        velthoric_tempRenderRot.getY(),
                        velthoric_tempRenderRot.getZ(),
                        velthoric_tempRenderRot.getW()
                );

                poseStack.mulPose(physRotation);
            });
        }
    }

    /**
     * Redirects the call to {@code entity.getViewVector(partialTicks)} when rendering a hitbox.
     * The PoseStack is already rotated by the vehicle's transform from the main {@code render} method.
     * However, the entity's view vector is also transformed to world-space by another mixin.
     * This would result in a double transformation when drawing the view vector line.
     * This redirect corrects this by transforming the world-space view vector back into the vehicle's
     * local space before it is rendered, ensuring it's drawn correctly.
     *
     * @param instance     The entity instance on which getViewVector() is called.
     * @param partialTicks The partial tick value for interpolation.
     * @return The corrected, local-space view vector for rendering.
     */
    @Redirect(
            method = "renderHitbox",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getViewVector(F)Lnet/minecraft/world/phys/Vec3;"
            )
    )
    private static Vec3 velthoric_redirectViewVectorForHitbox(Entity instance, float partialTicks) {
        Vec3 originalViewVector = instance.getViewVector(partialTicks);

        if (instance.getVehicle() instanceof VxMountingEntity proxy) {
            return proxy.getPhysicsBodyId().map(id -> {
                VxClientBodyManager manager = VxClientBodyManager.getInstance();
                VxClientBodyDataStore store = manager.getStore();
                VxClientBodyInterpolator interpolator = manager.getInterpolator();
                Integer index = store.getIndexForId(id);

                if (index == null || !store.render_isInitialized[index]) {
                    return originalViewVector;
                }

                interpolator.interpolateRotation(store, index, partialTicks, velthoric_tempRenderRot);

                Quaterniond physRotation = new Quaterniond(
                        velthoric_tempRenderRot.getX(),
                        velthoric_tempRenderRot.getY(),
                        velthoric_tempRenderRot.getZ(),
                        velthoric_tempRenderRot.getW()
                );

                // Invert the rotation to transform the world-space vector back to local-space.
                physRotation.invert();

                Vector3d correctedVector = new Vector3d(originalViewVector.x, originalViewVector.y, originalViewVector.z);
                physRotation.transform(correctedVector);

                return new Vec3(correctedVector.x, correctedVector.y, correctedVector.z);
            }).orElse(originalViewVector);
        }

        return originalViewVector;
    }
}