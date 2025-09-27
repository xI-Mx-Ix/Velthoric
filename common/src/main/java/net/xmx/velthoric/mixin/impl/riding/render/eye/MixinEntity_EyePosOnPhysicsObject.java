/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.riding.render.eye;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.physics.object.client.VxClientObjectDataStore;
import net.xmx.velthoric.physics.object.client.VxClientObjectInterpolator;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.riding.VxRidingProxyEntity;
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

    @Unique
    private static final RVec3 velthoric_interpolatedPosition_entity = new RVec3();
    @Unique
    private static final Quat velthoric_interpolatedRotation_entity = new Quat();

    @Inject(method = "getEyePosition(F)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private void velthoric_getEyePositionOnPhysicsObject(float partialTicks, CallbackInfoReturnable<Vec3> cir) {
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

    @Inject(method = "calculateViewVector", at = @At("HEAD"), cancellable = true)
    private void velthoric_calculateViewVectorOnPhysicsObject(float xRot, float yRot, CallbackInfoReturnable<Vec3> cir) {
        if (getVehicle() instanceof VxRidingProxyEntity proxy) {
            proxy.getPhysicsObjectId().ifPresent(id -> {
                VxClientObjectManager manager = VxClientObjectManager.getInstance();
                VxClientObjectDataStore store = manager.getStore();
                Integer index = store.getIndexForId(id);

                if (index == null || !store.render_isInitialized[index]) {
                    return;
                }

                Quat physRotQuat = new Quat(store.render_rotX[index], store.render_rotY[index], store.render_rotZ[index], store.render_rotW[index]);
                Quaterniond physRotation = new Quaterniond(physRotQuat.getX(), physRotQuat.getY(), physRotQuat.getZ(), physRotQuat.getW());

                float f = xRot * ((float)Math.PI / 180F);
                float g = -yRot * ((float)Math.PI / 180F);
                float h = Mth.cos(g);
                float i = Mth.sin(g);
                float j = Mth.cos(f);
                float k = Mth.sin(f);
                Vector3d originalViewVector = new Vector3d(i * j, -k, h * j);

                physRotation.transform(originalViewVector);

                cir.setReturnValue(new Vec3(originalViewVector.x, originalViewVector.y, originalViewVector.z));
            });
        }
    }
}
