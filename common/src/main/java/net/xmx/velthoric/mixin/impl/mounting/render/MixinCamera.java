/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.mounting.render;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.physics.mounting.entity.VxMountingEntity;
import net.xmx.velthoric.physics.object.client.VxClientObjectDataStore;
import net.xmx.velthoric.physics.object.client.VxClientObjectInterpolator;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
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

/**
 * Modifies the Camera to correctly follow an entity mounted on a physics-driven object.
 * It overrides the standard camera setup to derive its position and rotation from the
 * physics object's interpolated state, ensuring the camera moves and rotates smoothly
 * with the vehicle.
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
     * A reusable RVec3 to store interpolated position data, avoiding re-allocation each frame.
     */
    @Unique
    private static final RVec3 velthoric_interpolatedPosition = new RVec3();

    /**
     * A reusable Quat to store interpolated rotation data, avoiding re-allocation each frame.
     */
    @Unique
    private static final Quat velthoric_interpolatedRotation = new Quat();

    /**
     * Injects into the camera `setup` method to completely replace its logic when the focused
     * entity is mounted on a physics object.
     *
     * @param focusedEntity The entity the camera is focused on.
     * @param thirdPerson   Whether the camera is in a third-person view.
     * @param inverseView   Whether the camera is in the inverse third-person view.
     * @param partialTick   The fraction of a tick for interpolation.
     * @param ci            The callback info, used to cancel the original method.
     */
    @Inject(method = "setup", at = @At("HEAD"), cancellable = true)
    private void velthoric_followPhysicsObject(BlockGetter area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float partialTick, CallbackInfo ci) {
        if (focusedEntity.getVehicle() instanceof VxMountingEntity proxy) {
            proxy.getPhysicsObjectId().ifPresent(id -> {
                VxClientObjectManager manager = VxClientObjectManager.getInstance();
                VxClientObjectDataStore store = manager.getStore();
                VxClientObjectInterpolator interpolator = manager.getInterpolator();
                Integer index = store.getIndexForId(id);

                if (index == null || !store.render_isInitialized[index]) {
                    return;
                }

                // Interpolate the physics state for a smooth camera motion.
                interpolator.interpolateFrame(store, index, partialTick, velthoric_interpolatedPosition, velthoric_interpolatedRotation);

                Quaternionf physRotation = new Quaternionf(
                        velthoric_interpolatedRotation.getX(),
                        velthoric_interpolatedRotation.getY(),
                        velthoric_interpolatedRotation.getZ(),
                        velthoric_interpolatedRotation.getW()
                );

                // Calculate the base position of the passenger.
                Vector3f rideOffset = new Vector3f(proxy.getMountPositionOffset());
                physRotation.transform(rideOffset);
                Vector3d playerBasePos = new Vector3d(
                        velthoric_interpolatedPosition.xx(),
                        velthoric_interpolatedPosition.yy(),
                        velthoric_interpolatedPosition.zz()
                ).add(rideOffset.x(), rideOffset.y(), rideOffset.z());

                // Calculate the final eye position, applying the physics rotation to the eye height offset.
                double eyeY = Mth.lerp(partialTick, this.eyeHeightOld, this.eyeHeight);
                Vector3f eyeOffset = new Vector3f(0.0f, (float) eyeY, 0.0f);
                physRotation.transform(eyeOffset);
                Vector3d playerEyePos = playerBasePos.add(eyeOffset.x(), eyeOffset.y(), eyeOffset.z());

                // Set up basic camera state.
                this.initialized = true;
                this.level = area;
                this.entity = focusedEntity;
                this.detached = thirdPerson;

                float currentYaw = focusedEntity.getViewYRot(partialTick);
                float currentPitch = focusedEntity.getViewXRot(partialTick);

                // Set camera rotation and position.
                velthoric_setRotationWithPhysicsTransform(currentYaw, currentPitch, physRotation);
                this.setPosition(playerEyePos.x(), playerEyePos.y(), playerEyePos.z());

                // Handle third-person zoom and camera positioning.
                if (thirdPerson) {
                    if (inverseView) {
                        velthoric_setRotationWithPhysicsTransform(currentYaw + 180.0F, -currentPitch, physRotation);
                    }
                    if (this.level instanceof Level) {
                        // Custom zoom calculation is needed to ignore the physics object itself in the raycast.
                        this.move(-velthoric_getMaxZoomIgnoringPhysicsObject(4.0), 0.0, 0.0);
                    } else {
                        this.move(-this.getMaxZoom(4.0), 0.0, 0.0);
                    }
                }
                ci.cancel();
            });
        }
    }

    /**
     * Sets the camera's rotation by combining the entity's view angles (yaw, pitch) with the
     * vehicle's physics-based rotation. This ensures the camera's orientation is relative
     * to the vehicle it is mounted on.
     *
     * @param yaw          The entity's yaw.
     * @param pitch        The entity's pitch.
     * @param physRotation The vehicle's physics rotation.
     */
    @Unique
    private void velthoric_setRotationWithPhysicsTransform(float yaw, float pitch, Quaternionf physRotation) {
        // Start with the player's local camera rotation.
        Quaterniond originalRotation = new Quaterniond()
                .rotateY(Math.toRadians(-yaw))
                .rotateX(Math.toRadians(pitch))
                .normalize();

        // Combine it with the vehicle's world rotation.
        Quaterniond newRotation = new Quaterniond(physRotation).mul(originalRotation);
        
        this.xRot = pitch;
        this.yRot = yaw;
        this.rotation.set(newRotation);
        
        // Recalculate camera direction vectors.
        this.forwards.set(0.0F, 0.0F, 1.0F).rotate(this.rotation);
        this.up.set(0.0F, 1.0F, 0.0F).rotate(this.rotation);
        this.left.set(1.0F, 0.0F, 0.0F).rotate(this.rotation);
    }

    /**
     * Calculates the maximum camera zoom distance in third-person view, performing a raycast
     * to prevent clipping through blocks. This version is specifically designed to ignore the
     * physics object the player is mounted on, which would otherwise obstruct the view.
     *
     * @param maxZoom The desired maximum zoom distance.
     * @return The adjusted zoom distance after checking for collisions.
     */
    @Unique
    private double velthoric_getMaxZoomIgnoringPhysicsObject(double maxZoom) {
        if (!(this.level instanceof Level worldLevel)) return maxZoom;

        for (int i = 0; i < 8; ++i) {
            float f = (float) ((i & 1) * 2 - 1);
            float g = (float) ((i >> 1 & 1) * 2 - 1);
            float h = (float) ((i >> 2 & 1) * 2 - 1);
            f *= 0.1F;
            g *= 0.1F;
            h *= 0.1F;
            
            final Vec3 start = this.position.add(f, g, h);
            final Vec3 end = new Vec3(
                    this.position.x - (double) this.forwards.x() * maxZoom + (double) f + (double) h,
                    this.position.y - (double) this.forwards.y() * maxZoom + (double) g,
                    this.position.z - (double) this.forwards.z() * maxZoom + (double) h
            );
            
            // The ClipContext uses `this.entity` which is correctly set to the player,
            // implicitly ignoring the player and their vehicle in the raycast.
            final HitResult hitResult = worldLevel.clip(new ClipContext(
                    start, end, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, this.entity
            ));
            
            if (hitResult.getType() != HitResult.Type.MISS) {
                final double dist = hitResult.getLocation().distanceTo(this.position);
                if (dist < maxZoom) {
                    maxZoom = dist;
                }
            }
        }
        return maxZoom;
    }
}