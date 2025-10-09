/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.mounting.render;

import com.github.stephengold.joltjni.Quat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.physics.mounting.entity.VxMountingEntity;
import net.xmx.velthoric.physics.object.client.VxClientObjectDataStore;
import net.xmx.velthoric.physics.object.client.VxClientObjectInterpolator;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
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
 * Modifies the EntityRenderDispatcher to correctly render the hitboxes of entities
 * that are mounted on a physics-driven object. This ensures the hitbox (AABB) and
 * the view vector (blue line) are rotated according to the vehicle's physics state.
 *
 * @author xI-Mx-Ix
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class MixinEntityRenderDispatcher {

    /**
     * A temporary quaternion to store interpolated rotation data, avoiding re-allocation each frame.
     */
    @Unique
    private static final Quat velthoric_tempHitboxRot = new Quat();

    /**
     * Injects before rendering the hitbox to apply the physics-based rotation. This rotates
     * the entire rendering context (PoseStack) to match the orientation of the physics vehicle.
     * The PoseStack state is pushed to isolate this transformation.
     *
     * @param poseStack The current PoseStack for rendering transformations.
     * @param entity    The entity whose hitbox is being rendered.
     * @param ci        Callback info provided by Mixin.
     */
    @Inject(
            method = "renderHitbox(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/entity/Entity;F)V",
            at = @At("HEAD")
    )
    private static void velthoric_applyFullHitboxTransform(PoseStack poseStack, VertexConsumer buffer, Entity entity, float partialTicks, CallbackInfo ci) {
        if (entity.getVehicle() instanceof VxMountingEntity proxy) {
            proxy.getPhysicsObjectId().ifPresent(id -> {
                VxClientObjectManager manager = VxClientObjectManager.getInstance();
                VxClientObjectDataStore store = manager.getStore();
                VxClientObjectInterpolator interpolator = manager.getInterpolator();
                Integer index = store.getIndexForId(id);

                if (index == null || !store.render_isInitialized[index]) {
                    return;
                }

                interpolator.interpolateRotation(store, index, partialTicks, velthoric_tempHitboxRot);

                Quaternionf physRotation = new Quaternionf(
                        velthoric_tempHitboxRot.getX(),
                        velthoric_tempHitboxRot.getY(),
                        velthoric_tempHitboxRot.getZ(),
                        velthoric_tempHitboxRot.getW()
                );

                poseStack.pushPose();
                poseStack.mulPose(physRotation);
            });
        }
    }

    /**
     * Redirects the call to {@code entity.getViewVector(partialTicks)} to provide a corrected view vector.
     * The PoseStack is already rotated by the vehicle's transform. If the original world-space view
     * vector were used, it would be rotated a second time. This redirect provides the view vector in the
     * entity's local space by transforming it by the *inverse* of the vehicle's rotation. The rotated
     * PoseStack then correctly transforms this local vector back into the proper world-space orientation.
     *
     * @param instance     The entity instance on which getViewVector() is called.
     * @param partialTicks The partial tick value for interpolation.
     * @return The corrected, local-space view vector.
     */
    @Redirect(
            method = "renderHitbox(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/entity/Entity;F)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getViewVector(F)Lnet/minecraft/world/phys/Vec3;"
            )
    )
    private static Vec3 velthoric_redirectViewVectorForHitbox(Entity instance, float partialTicks) {
        Vec3 originalViewVector = instance.getViewVector(partialTicks);

        if (instance.getVehicle() instanceof VxMountingEntity proxy) {
            return proxy.getPhysicsObjectId().map(id -> {
                VxClientObjectManager manager = VxClientObjectManager.getInstance();
                VxClientObjectDataStore store = manager.getStore();
                VxClientObjectInterpolator interpolator = manager.getInterpolator();
                Integer index = store.getIndexForId(id);

                if (index == null || !store.render_isInitialized[index]) {
                    return originalViewVector;
                }

                interpolator.interpolateRotation(store, index, partialTicks, velthoric_tempHitboxRot);

                // Use Quaterniond for compatibility with Vector3d
                Quaterniond physRotation = new Quaterniond(
                        velthoric_tempHitboxRot.getX(),
                        velthoric_tempHitboxRot.getY(),
                        velthoric_tempHitboxRot.getZ(),
                        velthoric_tempHitboxRot.getW()
                );

                // Invert the rotation to transform from world-space back to local-space.
                physRotation.invert();

                Vector3d correctedVector = new Vector3d(originalViewVector.x, originalViewVector.y, originalViewVector.z);
                correctedVector.rotate(physRotation); // Now this works as both are double precision

                return new Vec3(correctedVector.x, correctedVector.y, correctedVector.z);
            }).orElse(originalViewVector);
        }
        
        return originalViewVector;
    }

    /**
     * Injects at the end of hitbox rendering to restore the original PoseStack state. This
     * prevents the physics-based rotation from affecting any other rendering operations by popping
     * the transformation that was pushed earlier.
     *
     * @param poseStack The current PoseStack.
     * @param entity    The entity whose hitbox was rendered.
     * @param ci        Callback info provided by Mixin.
     */
    @Inject(
            method = "renderHitbox(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/entity/Entity;F)V",
            at = @At("TAIL")
    )
    private static void velthoric_restoreHitboxTransform(PoseStack poseStack, VertexConsumer buffer, Entity entity, float partialTicks, CallbackInfo ci) {
        if (entity.getVehicle() instanceof VxMountingEntity proxy && proxy.getPhysicsObjectId().isPresent()) {
            poseStack.popPose();
        }
    }
}