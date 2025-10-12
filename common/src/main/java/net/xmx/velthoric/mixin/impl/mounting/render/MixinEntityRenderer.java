/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.mounting.render;

import com.github.stephengold.joltjni.Quat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.xmx.velthoric.physics.mounting.entity.VxMountingEntity;
import net.xmx.velthoric.physics.object.client.VxClientObjectDataStore;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects into {@link EntityRenderer} to adjust the nametag orientation
 * when an entity is mounted on a rotated physics object.
 * <p>
 * Normally, the nametag faces the camera using the entity's own rotation.
 * However, when the entity is attached to a physics-driven object (like a rotated vehicle),
 * this mixin ensures the nametag still faces the camera correctly by
 * temporarily inverting the physics rotation before the camera orientation is applied.
 * </p>
 *
 * @author xI-Mx-Ix
 */
@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer {

    @Unique
    private static final Quat velthoric_tempRenderRot = new Quat();

    /**
     * Injected before the camera orientation is applied to the nametag.
     * <p>
     * This temporarily inverts the rotation of the mounted physics object
     * to ensure that the nametag always faces the camera, regardless
     * of the parent object's world rotation.
     * </p>
     *
     * @param entity       The entity whose nametag is being rendered.
     * @param displayName  The name component to render.
     * @param poseStack    The current rendering transformation stack.
     * @param buffer       The render buffer used for drawing.
     * @param packedLight  The packed light value for rendering.
     * @param ci           The callback info provided by Mixin.
     * @param <T>          The type of entity being rendered.
     */
    @Inject(
            method = "renderNameTag",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionf;)V",
                    ordinal = 0
            )
    )
    private <T extends Entity> void velthoric_invertPhysicsRotationBeforeCameraOrientation(
            T entity, Component displayName, PoseStack poseStack, MultiBufferSource buffer,
            int packedLight, CallbackInfo ci) {

        if (!(entity.getVehicle() instanceof VxMountingEntity proxy)) {
            return;
        }

        proxy.getPhysicsObjectId().ifPresent(id -> {
            VxClientObjectManager manager = VxClientObjectManager.getInstance();
            VxClientObjectDataStore store = manager.getStore();
            Integer index = store.getIndexForId(id);

            if (index == null || !store.render_isInitialized[index]) {
                return;
            }

            float partialTicks = Minecraft.getInstance().getFrameTime();
            manager.getInterpolator().interpolateRotation(store, index, partialTicks, velthoric_tempRenderRot);

            Quaternionf physRotation = new Quaternionf(
                    velthoric_tempRenderRot.getX(),
                    velthoric_tempRenderRot.getY(),
                    velthoric_tempRenderRot.getZ(),
                    velthoric_tempRenderRot.getW()
            );

            physRotation.conjugate();
            poseStack.mulPose(physRotation);
        });
    }
}