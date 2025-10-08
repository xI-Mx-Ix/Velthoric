/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.riding.render.eye;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.physics.object.client.VxClientObjectDataStore;
import net.xmx.velthoric.physics.object.client.VxClientObjectInterpolator;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.object.type.VxBody;
import net.xmx.velthoric.physics.riding.VxRidingProxyEntity;
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
 * @author xI-Mx-Ix
 */
@Mixin(Entity.class)
public abstract class MixinEntity_EyePosOnPhysicsObject {

    @Shadow public abstract float getEyeHeight();
    @Shadow public abstract Entity getVehicle();
    @Shadow private Level level;

    @Unique
    private static final RVec3 velthoric_interpolatedPosition_entity = new RVec3();
    @Unique
    private static final Quat velthoric_interpolatedRotation_entity = new Quat();

    @Inject(method = "getEyePosition(F)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private void velthoric_getEyePositionOnPhysicsObject(float partialTicks, CallbackInfoReturnable<Vec3> cir) {
        if (!this.level.isClientSide()) {
            return;
        }

        if (getVehicle() instanceof VxRidingProxyEntity proxy) {
            proxy.getPhysicsObjectId().ifPresent(id -> {
                VxClientObjectManager manager = VxClientObjectManager.getInstance();
                VxClientObjectDataStore store = manager.getStore();
                VxClientObjectInterpolator interpolator = manager.getInterpolator();
                Integer index = store.getIndexForId(id);

                if (index == null || !store.render_isInitialized[index]) {
                    return;
                }

                interpolator.interpolateFrame(store, index, partialTicks, velthoric_interpolatedPosition_entity, velthoric_interpolatedRotation_entity);

                Quaterniond physRotation = new Quaterniond(
                        velthoric_interpolatedRotation_entity.getX(),
                        velthoric_interpolatedRotation_entity.getY(),
                        velthoric_interpolatedRotation_entity.getZ(),
                        velthoric_interpolatedRotation_entity.getW()
                );

                Vector3f rideOffset = new Vector3f(proxy.getRidePositionOffset());
                physRotation.transform(rideOffset);
                Vector3d playerBasePos = new Vector3d(
                        velthoric_interpolatedPosition_entity.xx(),
                        velthoric_interpolatedPosition_entity.yy(),
                        velthoric_interpolatedPosition_entity.zz()
                ).add(rideOffset.x(), rideOffset.y(), rideOffset.z());

                Vector3d eyeOffset = new Vector3d(0.0, this.getEyeHeight(), 0.0);
                physRotation.transform(eyeOffset);

                Vector3d finalEyePos = playerBasePos.add(eyeOffset);
                cir.setReturnValue(new Vec3(finalEyePos.x, finalEyePos.y, finalEyePos.z));
            });
        }
    }

    /**
     * Injects into the core view vector calculation method to apply the vehicle's rotation.
     * This single injection corrects the view vector for both the client (visuals) and server (logic).
     */
    @Inject(method = "calculateViewVector(FF)Lnet/minecraft/world/phys/Vec3;", at = @At("RETURN"), cancellable = true)
    private void velthoric_transformViewVector(float xRot, float yRot, CallbackInfoReturnable<Vec3> cir) {
        Entity self = (Entity)(Object)this;
        if (!(self.getVehicle() instanceof VxRidingProxyEntity proxy)) {
            return; // Not riding our vehicle, do nothing.
        }

        proxy.getPhysicsObjectId().ifPresent(id -> {
            // Get the original, untransformed local view vector calculated by Minecraft.
            Vec3 localViewVector = cir.getReturnValue();
            Vector3d transformedVector = new Vector3d(localViewVector.x, localViewVector.y, localViewVector.z);
            Quaterniond vehicleRotation;

            if (this.level.isClientSide()) {
                // --- CLIENT-SIDE LOGIC ---
                VxClientObjectManager manager = VxClientObjectManager.getInstance();
                VxClientObjectDataStore store = manager.getStore();
                VxClientObjectInterpolator interpolator = manager.getInterpolator();
                Integer index = store.getIndexForId(id);

                if (index == null || !store.render_isInitialized[index]) {
                    return;
                }

                // Use interpolated rotation for smooth visuals.
                float partialTicks = Minecraft.getInstance().getFrameTime();
                interpolator.interpolateRotation(store, index, partialTicks, velthoric_interpolatedRotation_entity);
                vehicleRotation = new Quaterniond(
                        velthoric_interpolatedRotation_entity.getX(),
                        velthoric_interpolatedRotation_entity.getY(),
                        velthoric_interpolatedRotation_entity.getZ(),
                        velthoric_interpolatedRotation_entity.getW()
                );

            } else {
                // --- SERVER-SIDE LOGIC ---
                VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(this.level.dimension());
                if (physicsWorld == null) return;
                VxBody body = physicsWorld.getObjectManager().getObject(id);
                if (body == null) return;

                // Use the current, exact rotation for accurate game logic.
                var rot = body.getTransform().getRotation();
                vehicleRotation = new Quaterniond(rot.getX(), rot.getY(), rot.getZ(), rot.getW());
            }

            // Apply the vehicle's rotation to the player's local view vector.
            vehicleRotation.transform(transformedVector);
            cir.setReturnValue(new Vec3(transformedVector.x, transformedVector.y, transformedVector.z));
        });
    }
}