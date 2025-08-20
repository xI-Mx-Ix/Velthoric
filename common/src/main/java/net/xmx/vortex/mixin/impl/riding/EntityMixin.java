package net.xmx.vortex.mixin.impl.riding;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.xmx.vortex.physics.object.physicsobject.client.ClientObjectDataManager;
import net.xmx.vortex.physics.object.physicsobject.client.interpolation.InterpolationFrame;
import net.xmx.vortex.physics.object.physicsobject.client.interpolation.RenderState;
import net.xmx.vortex.physics.object.riding.RidingProxyEntity;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @Shadow public abstract float getEyeHeight();
    @Shadow public abstract Entity getVehicle();

    @Unique
    private static final RenderState vortex_reusableRenderState_entity = new RenderState();

    @Inject(method = "getEyePosition(F)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private void vortex_getEyePositionOnPhysicsObject(float partialTicks, CallbackInfoReturnable<Vec3> cir) {
        if (getVehicle() instanceof RidingProxyEntity proxy) {
            proxy.getPhysicsObjectId().ifPresent(id -> {
                InterpolationFrame frame = ClientObjectDataManager.getInstance().getInterpolationFrame(id);
                if (frame == null || !frame.isInitialized) {
                    return;
                }

                frame.interpolate(vortex_reusableRenderState_entity, partialTicks);
                RVec3 physPos = vortex_reusableRenderState_entity.transform.getTranslation();
                Quat physRotQuat = vortex_reusableRenderState_entity.transform.getRotation();
                Quaterniond physRotation = new Quaterniond(physRotQuat.getX(), physRotQuat.getY(), physRotQuat.getZ(), physRotQuat.getW());

                Vector3f rideOffset = new Vector3f(proxy.getRidePositionOffset());
                physRotation.transform(rideOffset);
                Vector3d playerBasePos = new Vector3d(physPos.x(), physPos.y(), physPos.z())
                        .add(rideOffset.x(), rideOffset.y(), rideOffset.z());

                Vector3d eyeOffset = new Vector3d(0.0, this.getEyeHeight(), 0.0);
                physRotation.transform(eyeOffset);

                Vector3d finalEyePos = playerBasePos.add(eyeOffset);
                cir.setReturnValue(new Vec3(finalEyePos.x, finalEyePos.y, finalEyePos.z));
            });
        }
    }

    @Inject(method = "calculateViewVector", at = @At("HEAD"), cancellable = true)
    private void vortex_calculateViewVectorOnPhysicsObject(float xRot, float yRot, CallbackInfoReturnable<Vec3> cir) {
        if (getVehicle() instanceof RidingProxyEntity proxy) {
            proxy.getPhysicsObjectId().ifPresent(id -> {
                InterpolationFrame frame = ClientObjectDataManager.getInstance().getInterpolationFrame(id);
                if (frame == null || !frame.isInitialized) {
                    return;
                }
                
                frame.interpolate(vortex_reusableRenderState_entity, 0.0f);
                Quat physRotQuat = vortex_reusableRenderState_entity.transform.getRotation();
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