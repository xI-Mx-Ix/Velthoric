/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.mounting.entity;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.physics.mounting.entity.VxMountingEntity;
import net.xmx.velthoric.physics.body.client.VxClientBodyDataStore;
import net.xmx.velthoric.physics.body.client.VxClientBodyInterpolator;
import net.xmx.velthoric.physics.body.client.VxClientBodyManager;
import net.xmx.velthoric.physics.body.type.VxBody;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Modifies the Entity class to correctly calculate eye position and view vectors for entities
 * that are mounted on a physics-driven body. This ensures that camera placement, raycasting,
 * and projectile spawning originate from the correct, rotated locations.
 *
 * @author xI-Mx-Ix
 */
@Mixin(Entity.class)
public abstract class MixinEntity {

    @Shadow public abstract float getEyeHeight();
    @Shadow public abstract Entity getVehicle();
    @Shadow private Level level;

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
     * Injects into `getEyePosition` to return the correctly transformed eye position of an entity
     * mounted on a physics body. It combines the vehicle's interpolated position, the passenger's
     * local offset, and the entity's eye height, all transformed by the vehicle's rotation.
     *
     * @param partialTicks The fraction of a tick for interpolation.
     * @param cir The callback info, used to set the return value.
     */
    @Inject(method = "getEyePosition(F)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private void velthoric_getEyePositionOnPhysicsBody(float partialTicks, CallbackInfoReturnable<Vec3> cir) {
        if (!this.level.isClientSide()) {
            return;
        }

        if (getVehicle() instanceof VxMountingEntity proxy) {
            proxy.getPhysicsBodyId().ifPresent(id -> {
                VxClientBodyManager manager = VxClientBodyManager.getInstance();
                VxClientBodyDataStore store = manager.getStore();
                VxClientBodyInterpolator interpolator = manager.getInterpolator();
                Integer index = store.getIndexForId(id);

                if (index == null || !store.render_isInitialized[index]) {
                    return;
                }

                interpolator.interpolateFrame(store, index, partialTicks, velthoric_interpolatedPosition, velthoric_interpolatedRotation);

                Quaterniond physRotation = new Quaterniond(
                        velthoric_interpolatedRotation.getX(),
                        velthoric_interpolatedRotation.getY(),
                        velthoric_interpolatedRotation.getZ(),
                        velthoric_interpolatedRotation.getW()
                );

                // Calculate base position with ride offset
                Vector3f rideOffset = new Vector3f(proxy.getMountPositionOffset());
                physRotation.transform(rideOffset);
                Vector3d playerBasePos = new Vector3d(
                        velthoric_interpolatedPosition.xx(),
                        velthoric_interpolatedPosition.yy(),
                        velthoric_interpolatedPosition.zz()
                ).add(rideOffset.x(), rideOffset.y(), rideOffset.z());

                // Calculate and apply rotated eye height offset
                Vector3d eyeOffset = new Vector3d(0.0, this.getEyeHeight(), 0.0);
                physRotation.transform(eyeOffset);

                Vector3d finalEyePos = playerBasePos.add(eyeOffset);
                cir.setReturnValue(new Vec3(finalEyePos.x, finalEyePos.y, finalEyePos.z));
            });
        }
    }

    /**
     * Injects into the view vector calculation method to apply the vehicle's rotation.
     * This transforms the entity's local view vector (based on yaw/pitch) into world space,
     * accounting for the vehicle's orientation. This is critical for both client-side visuals
     * and server-side game logic (e.g., aiming).
     *
     * @param cir The callback info, used to set the return value.
     */
    @Inject(method = "calculateViewVector(FF)Lnet/minecraft/world/phys/Vec3;", at = @At("RETURN"), cancellable = true)
    private void velthoric_transformViewVector(float xRot, float yRot, CallbackInfoReturnable<Vec3> cir) {
        Entity self = (Entity)(Object)this;
        if (!(self.getVehicle() instanceof VxMountingEntity proxy)) {
            return;
        }

        proxy.getPhysicsBodyId().ifPresent(id -> {
            // Get the original, untransformed local view vector.
            Vec3 localViewVector = cir.getReturnValue();
            Vector3d transformedVector = new Vector3d(localViewVector.x, localViewVector.y, localViewVector.z);
            Quaterniond vehicleRotation;

            if (this.level.isClientSide()) {
                // On the client, use interpolated rotation for smooth visuals.
                VxClientBodyManager manager = VxClientBodyManager.getInstance();
                VxClientBodyDataStore store = manager.getStore();
                Integer index = store.getIndexForId(id);

                if (index == null || !store.render_isInitialized[index]) {
                    return;
                }
                
                float partialTicks = Minecraft.getInstance().getFrameTime();
                manager.getInterpolator().interpolateRotation(store, index, partialTicks, velthoric_interpolatedRotation);
                vehicleRotation = new Quaterniond(
                        velthoric_interpolatedRotation.getX(),
                        velthoric_interpolatedRotation.getY(),
                        velthoric_interpolatedRotation.getZ(),
                        velthoric_interpolatedRotation.getW()
                );

            } else {
                // On the server, use the exact physics rotation for accurate game logic.
                VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(this.level.dimension());
                if (physicsWorld == null) return;
                VxBody body = physicsWorld.getBodyManager().getVxBody(id);
                if (body == null) return;
                
                var rot = body.getTransform().getRotation();
                vehicleRotation = new Quaterniond(rot.getX(), rot.getY(), rot.getZ(), rot.getW());
            }

            // Apply the vehicle's rotation to the local view vector.
            vehicleRotation.transform(transformedVector);
            cir.setReturnValue(new Vec3(transformedVector.x, transformedVector.y, transformedVector.z));
        });
    }
}