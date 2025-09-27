/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.riding.render.camera;

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
import net.xmx.velthoric.physics.object.client.VxClientObjectInterpolator;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.object.client.VxClientObjectDataStore;
import net.xmx.velthoric.physics.riding.VxRidingProxyEntity;
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
import java.util.UUID;

/**
 * @author xI-Mx-Ix
 */
@Mixin(Camera.class)
public abstract class MixinCamera_CamOnPhysicsObject {

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

    @Unique
    private static final RVec3 velthoric_interpolatedPosition = new RVec3();
    @Unique
    private static final Quat velthoric_interpolatedRotation = new Quat();

    @Unique
    private void velthoric_setRotationWithPhysicsTransform(float yaw, float pitch, Quaternionf physRotation) {
        Quaterniond originalRotation = new Quaterniond()
                .rotateY(Math.toRadians(-yaw))
                .rotateX(Math.toRadians(pitch))
                .normalize();
        Quaterniond newRotation = new Quaterniond(physRotation).mul(originalRotation);
        this.xRot = pitch;
        this.yRot = yaw;
        this.rotation.set(newRotation);
        this.forwards.set(0.0F, 0.0F, 1.0F);
        this.rotation.transform(this.forwards);
        this.up.set(0.0F, 1.0F, 0.0F);
        this.rotation.transform(this.up);
        this.left.set(1.0F, 0.0F, 0.0F);
        this.rotation.transform(this.left);
    }

    @Unique
    private double velthoric_getMaxZoomIgnoringPhysicsObject(Level level, double maxZoom, UUID physicsObjectId) {
        for (int i = 0; i < 8; ++i) {
            float f = (float) ((i & 1) * 2 - 1);
            float g = (float) ((i >> 1 & 1) * 2 - 1);
            float h = (float) ((i >> 2 & 1) * 2 - 1);
            f *= 0.1F;
            g *= 0.1F;
            h *= 0.1F;
            final Vec3 vec3 = this.position.add(f, g, h);
            final Vec3 vec32 = new Vec3(
                    this.position.x - (double) this.forwards.x() * maxZoom + (double) f + (double) h,
                    this.position.y - (double) this.forwards.y() * maxZoom + (double) g,
                    this.position.z - (double) this.forwards.z() * maxZoom + (double) h
            );
            final HitResult hitResult = level.clip(new ClipContext(
                    vec3, vec32, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, this.entity
            ));
            if (hitResult.getType() != HitResult.Type.MISS) {
                final double e = hitResult.getLocation().distanceTo(this.position);
                if (e < maxZoom) {
                    maxZoom = e;
                }
            }
        }
        return maxZoom;
    }

    @Inject(method = "setup", at = @At("HEAD"), cancellable = true)
    private void velthoric_followPhysicsObject(BlockGetter area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float partialTick, CallbackInfo ci) {
        if (focusedEntity.getVehicle() instanceof VxRidingProxyEntity proxy) {
            proxy.getPhysicsObjectId().ifPresent(id -> {
                VxClientObjectManager manager = VxClientObjectManager.getInstance();
                VxClientObjectDataStore store = manager.getStore();
                VxClientObjectInterpolator interpolator = manager.getInterpolator();
                Integer index = store.getIndexForId(id);

                if (index == null || !store.render_isInitialized[index]) {
                    return;
                }

                interpolator.interpolateFrame(store, index, partialTick, velthoric_interpolatedPosition, velthoric_interpolatedRotation);

                Quaternionf physRotation = new Quaternionf(
                        velthoric_interpolatedRotation.getX(),
                        velthoric_interpolatedRotation.getY(),
                        velthoric_interpolatedRotation.getZ(),
                        velthoric_interpolatedRotation.getW()
                );

                Vector3f rideOffset = new Vector3f(proxy.getRidePositionOffset());
                physRotation.transform(rideOffset);
                Vector3d playerBasePos = new Vector3d(
                        velthoric_interpolatedPosition.xx(),
                        velthoric_interpolatedPosition.yy(),
                        velthoric_interpolatedPosition.zz()
                ).add(rideOffset.x(), rideOffset.y(), rideOffset.z());

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

                this.velthoric_setRotationWithPhysicsTransform(currentYaw, currentPitch, physRotation);
                this.setPosition(playerEyePos.x(), playerEyePos.y(), playerEyePos.z());

                if (thirdPerson) {
                    if (inverseView) {
                        this.velthoric_setRotationWithPhysicsTransform(currentYaw + 180.0F, -currentPitch, physRotation);
                    }
                    if (this.level instanceof Level) {
                        this.move(-this.velthoric_getMaxZoomIgnoringPhysicsObject((Level) this.level, 4.0, id), 0.0, 0.0);
                    } else {
                        this.move(-this.getMaxZoom(4.0), 0.0, 0.0);
                    }
                }
                ci.cancel();
            });
        }
    }
}
