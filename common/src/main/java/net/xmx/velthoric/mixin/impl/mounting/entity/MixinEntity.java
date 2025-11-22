/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.mounting.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.math.VxConversions;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.physics.body.type.VxBody;
import net.xmx.velthoric.physics.mounting.entity.VxMountingEntity;
import net.xmx.velthoric.physics.mounting.util.VxMountingRenderUtils;
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

import java.util.Optional;

/**
 * Modifies the Entity class to correctly calculate eye position and view vectors for entities
 * that are mounted on a physics-driven body.
 *
 * @author xI-Mx-Ix
 */
@Mixin(Entity.class)
public abstract class MixinEntity {

    @Shadow public abstract float getEyeHeight();
    @Shadow public abstract Entity getVehicle();
    @Shadow private Level level;

    /**
     * A reusable transform object to store interpolated physics state, avoiding re-allocation each frame.
     */
    @Unique
    private final VxTransform velthoric_interpolatedTransform = new VxTransform();

    /**
     * Injects into `getEyePosition` to return the correctly transformed eye position of an entity
     * mounted on a physics body.
     */
    @Inject(method = "getEyePosition(F)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private void velthoric_getEyePositionOnPhysicsBody(float partialTicks, CallbackInfoReturnable<Vec3> cir) {
        if (!this.level.isClientSide() || !(getVehicle() instanceof VxMountingEntity proxy)) {
            return;
        }

        VxMountingRenderUtils.INSTANCE.getInterpolatedTransform(proxy, partialTicks, velthoric_interpolatedTransform)
                .ifPresent(transform -> {
                    // Correctly construct a JOML Quaterniond from a Jolt Quat's components.
                    var joltRotation = transform.getRotation();
                    Quaterniond physRotation = new Quaterniond(joltRotation.getX(), joltRotation.getY(), joltRotation.getZ(), joltRotation.getW());

                    Vector3f rideOffset = new Vector3f(proxy.getMountPositionOffset());
                    physRotation.transform(rideOffset);
                    Vector3d playerBasePos = VxConversions.toJoml(transform.getTranslation(), new Vector3d()).add(rideOffset.x, rideOffset.y, rideOffset.z);

                    Vector3d eyeOffset = new Vector3d(0.0, this.getEyeHeight(), 0.0);
                    physRotation.transform(eyeOffset);

                    Vector3d finalEyePos = playerBasePos.add(eyeOffset);
                    cir.setReturnValue(VxConversions.toMinecraft(finalEyePos));
                });
    }

    /**
     * Injects into the view vector calculation method to apply the vehicle's rotation, transforming
     * the entity's local view vector into world space.
     */
    @Inject(method = "calculateViewVector(FF)Lnet/minecraft/world/phys/Vec3;", at = @At("RETURN"), cancellable = true)
    private void velthoric_transformViewVector(float xRot, float yRot, CallbackInfoReturnable<Vec3> cir) {
        // This is the standard way to reference the instance in a Mixin, avoiding IDE false positives.
        Entity self = (Entity) (Object) this;
        if (!(self.getVehicle() instanceof VxMountingEntity proxy)) {
            return;
        }

        proxy.getPhysicsId().ifPresent(id -> {
            Vec3 localViewVector = cir.getReturnValue();
            Vector3d transformedVector = VxConversions.toJoml(localViewVector, new Vector3d());
            Quaterniond vehicleRotation;

            if (this.level.isClientSide()) {
                // On the client, use interpolated rotation for smooth visuals.
                // Safely get the rotation from a client-only helper method.
                vehicleRotation = velthoric_getInterpolatedRotationClient(proxy).orElse(null);
                if (vehicleRotation == null) return;
            } else {
                // On the server, use the exact physics rotation for accurate game logic.
                VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(this.level.dimension());
                if (physicsWorld == null) return;
                VxBody body = physicsWorld.getBodyManager().getVxBody(id);
                if (body == null) return;

                var rot = body.getTransform().getRotation();
                vehicleRotation = new Quaterniond(rot.getX(), rot.getY(), rot.getZ(), rot.getW());
            }

            vehicleRotation.transform(transformedVector);
            cir.setReturnValue(VxConversions.toMinecraft(transformedVector));
        });
    }

    /**
     * A helper method to encapsulate client-only logic for retrieving the interpolated rotation.
     * This method is stripped out in a server environment by the Fabric loader, preventing a crash.
     *
     * @param proxy The mounting entity.
     * @return An Optional containing the interpolated rotation if on the client, otherwise empty.
     */
    @Unique
    @Environment(EnvType.CLIENT)
    private Optional<Quaterniond> velthoric_getInterpolatedRotationClient(VxMountingEntity proxy) {
        float partialTicks = Minecraft.getInstance().getFrameTime();
        return VxMountingRenderUtils.INSTANCE.getInterpolatedRotation(proxy, partialTicks)
                .map(q -> new Quaterniond(q.getX(), q.getY(), q.getZ(), q.getW()));
    }
}