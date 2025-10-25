/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.mounting.render;

import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.math.VxConversions;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.physics.mounting.entity.VxMountingEntity;
import net.xmx.velthoric.physics.mounting.util.VxMountingRenderUtils;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * Modifies the Camera to correctly follow an entity mounted on a physics-driven body.
 *
 * @author xI-Mx-Ix
 */
@Mixin(Camera.class)
public abstract class MixinCamera {

    @Shadow @Final private Quaternionf rotation;
    @Shadow @Final private Vector3f forwards;
    @Shadow @Final private Vector3f up;
    @Shadow @Final private Vector3f left;
    @Shadow private boolean initialized;
    @Shadow private BlockGetter level;
    @Shadow private boolean detached;
    @Shadow private Entity entity;
    @Shadow private float eyeHeight;
    @Shadow private float eyeHeightOld;
    @Shadow private float yRot;
    @Shadow private float xRot;
    @Shadow private Vec3 position;

    @Shadow protected abstract void setPosition(double x, double y, double z);
    @Shadow protected abstract void move(double distanceOffset, double verticalOffset, double horizontalOffset);
    @Shadow protected abstract double getMaxZoom(double startingDistance);

    /**
     * A reusable transform object to store interpolated physics state, avoiding re-allocation each frame.
     */
    @Unique
    private final VxTransform velthoric_interpolatedTransform = new VxTransform();

    /**
     * Injects into the camera `setup` method to completely replace its logic when the focused
     * entity is mounted on a physics body.
     */
    @Inject(method = "setup", at = @At("HEAD"), cancellable = true)
    private void velthoric_followPhysicsBody(BlockGetter area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float partialTick, CallbackInfo ci) {
        if (focusedEntity.getVehicle() instanceof VxMountingEntity proxy) {
            Optional<VxTransform> transformOpt = VxMountingRenderUtils.INSTANCE.getInterpolatedTransform(proxy, partialTick, velthoric_interpolatedTransform);

            if (transformOpt.isPresent()) {
                VxTransform transform = transformOpt.get();
                Quaternionf physRotation = transform.getRotation(new Quaternionf());

                Vector3f rideOffset = new Vector3f(proxy.getMountPositionOffset());
                physRotation.transform(rideOffset);
                Vector3d playerBasePos = VxConversions.toJoml(transform.getTranslation(), new Vector3d()).add(rideOffset.x, rideOffset.y, rideOffset.z);

                double eyeY = Mth.lerp(partialTick, this.eyeHeightOld, this.eyeHeight);
                Vector3f eyeOffset = new Vector3f(0.0f, (float) eyeY, 0.0f);
                physRotation.transform(eyeOffset);
                Vector3d playerEyePos = playerBasePos.add(eyeOffset.x(), eyeOffset.y(), eyeOffset.z());

                this.initialized = true;
                this.level = area;
                this.entity = focusedEntity;
                this.detached = thirdPerson;

                float currentYaw = focusedEntity.getViewYRot(partialTick);
                float currentPitch = focusedEntity.getViewXRot(partialTick);

                velthoric_setRotationWithPhysicsTransform(currentYaw, currentPitch, physRotation);
                this.setPosition(playerEyePos.x, playerEyePos.y, playerEyePos.z);

                if (thirdPerson) {
                    if (inverseView) {
                        velthoric_setRotationWithPhysicsTransform(currentYaw + 180.0F, -currentPitch, physRotation);
                    }
                    this.move(-velthoric_getMaxZoomIgnoringPhysicsBody(4.0), 0.0, 0.0);
                }
                ci.cancel();
            }
        }
    }

    /**
     * Sets the camera's rotation by combining the entity's view angles with the vehicle's physics-based rotation.
     */
    @Unique
    private void velthoric_setRotationWithPhysicsTransform(float yaw, float pitch, Quaternionf physRotation) {
        Quaterniond originalRotation = new Quaterniond().rotateY(Math.toRadians(-yaw)).rotateX(Math.toRadians(pitch));
        Quaterniond newRotation = new Quaterniond(physRotation).mul(originalRotation).normalize();

        this.xRot = pitch;
        this.yRot = yaw;
        this.rotation.set(newRotation);

        this.forwards.set(0.0F, 0.0F, 1.0F).rotate(this.rotation);
        this.up.set(0.0F, 1.0F, 0.0F).rotate(this.rotation);
        this.left.set(1.0F, 0.0F, 0.0F).rotate(this.rotation);
    }

    /**
     * Calculates the maximum camera zoom distance in third-person view, performing a raycast
     * that correctly ignores the player and their vehicle.
     */
    @Unique
    private double velthoric_getMaxZoomIgnoringPhysicsBody(double maxZoom) {
        for (int i = 0; i < 8; ++i) {
            float f = (float) ((i & 1) * 2 - 1) * 0.1F;
            float g = (float) ((i >> 1 & 1) * 2 - 1) * 0.1F;
            float h = (float) ((i >> 2 & 1) * 2 - 1) * 0.1F;

            Vec3 start = this.position.add(f, g, h);
            Vec3 end = new Vec3(
                    this.position.x - (double) this.forwards.x() * maxZoom + f + h,
                    this.position.y - (double) this.forwards.y() * maxZoom + g,
                    this.position.z - (double) this.forwards.z() * maxZoom + h
            );

            HitResult hitResult = this.level.clip(new ClipContext(start, end, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, this.entity));

            if (hitResult.getType() != HitResult.Type.MISS) {
                double dist = hitResult.getLocation().distanceTo(this.position);
                if (dist < maxZoom) {
                    maxZoom = dist;
                }
            }
        }
        return maxZoom;
    }
}