/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.riding.render.bounds;

import com.github.stephengold.joltjni.Quat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.physics.object.client.VxClientObjectDataStore;
import net.xmx.velthoric.physics.object.client.VxClientObjectInterpolator;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.riding.VxRidingProxyEntity;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Modifies the {@link EntityRenderDispatcher} to correctly render hitboxes for entities
 * that are passengers of a physics-driven object. This includes rotating the entire
 * hitbox and ensuring the view vector (blue line) points in the correct direction.
 *
 * @author xI-Mx-Ix
 */
@Mixin(EntityRenderDispatcher.class)
public class MixinEntityRenderDispatcher_TransformHitbox {

    /**
     * A temporary quaternion to store interpolated rotation data, avoiding re-allocation each frame.
     */
    @Unique
    private static final Quat velthoric_tempHitboxRot = new Quat();

    /**
     * Injects at the beginning of the hitbox rendering method to apply the physics-based rotation.
     * This ensures that the entire hitbox, including the bounding box and the eye-level line,
     * is rotated to match the orientation of the physics vehicle. The PoseStack state is pushed
     * to isolate this transformation.
     *
     * @param poseStack The current PoseStack for rendering transformations.
     * @param entity The entity whose hitbox is being rendered.
     * @param partialTicks The fraction of a tick that has passed since the last full tick.
     * @param ci Callback info provided by Mixin.
     */
    @Inject(
            method = "renderHitbox",
            at = @At("HEAD")
    )
    private static void velthoric_applyFullHitboxTransform(PoseStack poseStack, VertexConsumer buffer, Entity entity, float partialTicks, CallbackInfo ci) {
        if (entity.getVehicle() instanceof VxRidingProxyEntity proxy) {
            proxy.getPhysicsObjectId().ifPresent(id -> {
                VxClientObjectManager manager = VxClientObjectManager.getInstance();
                VxClientObjectDataStore store = manager.getStore();
                VxClientObjectInterpolator interpolator = manager.getInterpolator();
                Integer index = store.getIndexForId(id);

                if (index == null || !store.render_isInitialized[index]) {
                    return;
                }

                // Interpolate the physics rotation for the current frame.
                interpolator.interpolateRotation(store, index, partialTicks, velthoric_tempHitboxRot);

                Quaternionf physRotation = new Quaternionf(
                        velthoric_tempHitboxRot.getX(),
                        velthoric_tempHitboxRot.getY(),
                        velthoric_tempHitboxRot.getZ(),
                        velthoric_tempHitboxRot.getW()
                );

                // Save the current state and apply the physics rotation.
                poseStack.pushPose();
                poseStack.mulPose(physRotation);
            });
        }
    }

    /**
     * Intercepts the view vector before it is used to render the blue direction line.
     * The entity's view vector is already correctly transformed into world-space by another mixin.
     * However, since the entire PoseStack is also rotated by {@link #velthoric_applyFullHitboxTransform},
     * rendering the world-space vector would apply the rotation a second time.
     * <p>
     * To counteract this, this method transforms the vector by the *inverse* of the physics rotation.
     * This effectively converts the vector back into the entity's local space. When the vanilla code
     * then renders this local-space vector, the rotated PoseStack correctly transforms it into the
     * proper world-space orientation, avoiding the double transformation.
     *
     * @param value The original view vector calculated by the game.
     * @param entity The entity being rendered.
     * @param partialTicks The fraction of a tick that has passed.
     * @return The corrected, local-space view vector.
     */
    @ModifyVariable(
            method = "renderHitbox",
            at = @At(value = "STORE", ordinal = 0),
            name = "vec3"
    )
    private static Vec3 velthoric_untransformViewVector(Vec3 value, PoseStack poseStack, VertexConsumer buffer, Entity entity, float partialTicks) {
        if (entity.getVehicle() instanceof VxRidingProxyEntity proxy) {
            return proxy.getPhysicsObjectId().map(id -> {
                VxClientObjectManager manager = VxClientObjectManager.getInstance();
                VxClientObjectDataStore store = manager.getStore();
                VxClientObjectInterpolator interpolator = manager.getInterpolator();
                Integer index = store.getIndexForId(id);

                if (index == null || !store.render_isInitialized[index]) {
                    return value;
                }

                interpolator.interpolateRotation(store, index, partialTicks, velthoric_tempHitboxRot);

                Quaternionf physRotation = new Quaternionf(
                        velthoric_tempHitboxRot.getX(),
                        velthoric_tempHitboxRot.getY(),
                        velthoric_tempHitboxRot.getZ(),
                        velthoric_tempHitboxRot.getW()
                );

                // Invert the rotation to transform from world-space back to local-space.
                physRotation.invert();

                Vector3d correctedVector = new Vector3d(value.x, value.y, value.z);
                physRotation.transform(correctedVector);

                return new Vec3(correctedVector.x, correctedVector.y, correctedVector.z);
            }).orElse(value);
        }
        return value;
    }

    /**
     * Injects at the end of the hitbox rendering method to restore the original PoseStack state.
     * This prevents the physics-based rotation from affecting other rendering operations by popping
     * the transformation that was pushed at the beginning of the method.
     *
     * @param poseStack The current PoseStack.
     * @param entity The entity whose hitbox was rendered.
     * @param ci Callback info provided by Mixin.
     */
    @Inject(
            method = "renderHitbox",
            at = @At("TAIL")
    )
    private static void velthoric_restoreHitboxTransform(PoseStack poseStack, VertexConsumer buffer, Entity entity, float partialTicks, CallbackInfo ci) {
        // If a transformation was applied at the beginning, pop it now to restore the original state.
        if (entity.getVehicle() instanceof VxRidingProxyEntity proxy && proxy.getPhysicsObjectId().isPresent()) {
            poseStack.popPose();
        }
    }
}