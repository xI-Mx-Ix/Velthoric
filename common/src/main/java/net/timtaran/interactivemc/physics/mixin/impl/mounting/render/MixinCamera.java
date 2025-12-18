/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.mixin.impl.mounting.render;

import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.timtaran.interactivemc.physics.math.VxConversions;
import net.timtaran.interactivemc.physics.math.VxTransform;
import net.timtaran.interactivemc.physics.physics.mounting.entity.VxMountingEntity;
import net.timtaran.interactivemc.physics.physics.mounting.util.VxMountingRenderUtils;
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
    @Shadow protected abstract void move(float distanceOffset, float verticalOffset, float horizontalOffset);

    @Unique
    private static final Vector3f FORWARDS_CONST = new Vector3f(0.0F, 0.0F, -1.0F);
    @Unique
    private static final Vector3f UP_CONST = new Vector3f(0.0F, 1.0F, 0.0F);
    @Unique
    private static final Vector3f LEFT_CONST = new Vector3f(-1.0F, 0.0F, 0.0F);

    /**
     * Reusable transform object to store interpolated physics state and avoid allocations.
     */
    @Unique
    private final VxTransform velthoric_interpolatedTransform = new VxTransform();

    /**
     * Intercepts camera setup to apply physics-based transformations when the focused entity
     * is mounted on a physics body. This method replicates vanilla camera behavior but transforms
     * all positions and rotations relative to the moving physics body's coordinate system.
     *
     * @param level The block getter for raycasting
     * @param focusedEntity The entity the camera is following
     * @param detached Whether the camera is in third-person mode
     * @param thirdPersonReverse Whether the camera is in front-facing third-person mode
     * @param partialTick The interpolation factor between ticks
     * @param ci Callback info for cancelling the original method
     */
    @Inject(method = "setup", at = @At("HEAD"), cancellable = true)
    private void velthoric_followPhysicsBody(BlockGetter level, Entity focusedEntity, boolean detached, boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
        if (focusedEntity.getVehicle() instanceof VxMountingEntity proxy) {
            Optional<VxTransform> transformOpt = VxMountingRenderUtils.INSTANCE.getInterpolatedTransform(proxy, partialTick, velthoric_interpolatedTransform);

            if (transformOpt.isPresent()) {
                VxTransform transform = transformOpt.get();
                Quaternionf physRotation = transform.getRotation(new Quaternionf());

                // Step 1: Calculate the base mounting position by applying the mount offset in physics space
                Vector3f rideOffset = new Vector3f(proxy.getMountPositionOffset());
                physRotation.transform(rideOffset);
                Vector3d playerBasePos = VxConversions.toJoml(transform.getTranslation(), new Vector3d())
                        .add(rideOffset.x, rideOffset.y, rideOffset.z);

                // Step 2: Add interpolated eye height offset in physics space
                double eyeY = Mth.lerp(partialTick, this.eyeHeightOld, this.eyeHeight);
                Vector3f eyeOffset = new Vector3f(0.0f, (float) eyeY, 0.0f);
                physRotation.transform(eyeOffset);
                Vector3d playerEyePos = playerBasePos.add(eyeOffset.x(), eyeOffset.y(), eyeOffset.z());

                // Step 3: Set camera state fields
                this.initialized = true;
                this.level = level;
                this.entity = focusedEntity;
                this.detached = detached;
                this.setPosition(playerEyePos.x, playerEyePos.y, playerEyePos.z);

                // Step 4: Calculate and apply camera rotation
                float yaw = focusedEntity.getViewYRot(partialTick);
                float pitch = focusedEntity.getViewXRot(partialTick);

                if (detached) {
                    // In third-person mode, optionally reverse view and pull camera back
                    if (thirdPersonReverse) {
                        yaw += 180.0F;
                        pitch = -pitch;
                    }

                    velthoric_setRotationWithPhysicsTransform(yaw, pitch, physRotation);

                    // Calculate zoom distance accounting for entity scale
                    float scale = 1.0F;
                    if (focusedEntity instanceof LivingEntity) {
                        scale = ((LivingEntity) focusedEntity).getScale();
                    }

                    float zoomDistance = velthoric_getMaxZoom(4.0F * scale);
                    this.move(-zoomDistance, 0.0F, 0.0F);
                } else {
                    // First-person mode only requires rotation
                    velthoric_setRotationWithPhysicsTransform(yaw, pitch, physRotation);
                }

                ci.cancel();
            }
        }
    }

    /**
     * Applies camera rotation by combining the physics body's world rotation with the entity's
     * local view rotation. This maintains the vanilla camera feel while properly orienting
     * relative to the moving physics body.
     *
     * The final rotation is computed as: PhysicsRotation * LocalViewRotation
     * This ensures the "up" direction follows the physics body while head movement remains intuitive.
     *
     * @param yaw The entity's yaw angle in degrees
     * @param pitch The entity's pitch angle in degrees
     * @param physRotation The physics body's world-space rotation
     */
    @Unique
    private void velthoric_setRotationWithPhysicsTransform(float yaw, float pitch, Quaternionf physRotation) {
        this.xRot = pitch;
        this.yRot = yaw;

        // Construct local view rotation using vanilla's exact formula
        Quaternionf localRotation = new Quaternionf().rotationYXZ(
                (float) Math.PI - yaw * ((float) Math.PI / 180F),
                -pitch * ((float) Math.PI / 180F),
                0.0F
        );

        // Combine physics and local rotations
        Quaternionf finalRotation = new Quaternionf(physRotation).mul(localRotation);
        this.rotation.set(finalRotation);

        // Update direction vectors by rotating vanilla's constant direction vectors
        FORWARDS_CONST.rotate(this.rotation, this.forwards);
        UP_CONST.rotate(this.rotation, this.up);
        LEFT_CONST.rotate(this.rotation, this.left);
    }

    /**
     * Calculates the maximum camera zoom distance by raycasting from multiple offset points
     * around the camera position. This prevents the camera from clipping through blocks
     * in third-person view.
     *
     * Uses 8 sample points in a cube pattern around the camera to ensure collision detection
     * from all angles.
     *
     * @param maxZoom The desired maximum zoom distance
     * @return The actual zoom distance, reduced if any raycast hits geometry
     */
    @Unique
    private float velthoric_getMaxZoom(float maxZoom) {
        for (int i = 0; i < 8; ++i) {
            // Generate offset pattern: +/- 0.1 in each axis
            float g = (float) ((i & 1) * 2 - 1);
            float h = (float) ((i >> 1 & 1) * 2 - 1);
            float j = (float) ((i >> 2 & 1) * 2 - 1);

            Vec3 vec3 = this.position.add((double) (g * 0.1F), (double) (h * 0.1F), (double) (j * 0.1F));
            Vec3 vec32 = vec3.add((new Vec3(this.forwards)).scale((double) (-maxZoom)));

            HitResult hitResult = this.level.clip(new ClipContext(vec3, vec32, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, this.entity));

            if (hitResult.getType() != HitResult.Type.MISS) {
                float k = (float) hitResult.getLocation().distanceToSqr(this.position);
                if (k < Mth.square(maxZoom)) {
                    maxZoom = Mth.sqrt(k);
                }
            }
        }
        return maxZoom;
    }
}