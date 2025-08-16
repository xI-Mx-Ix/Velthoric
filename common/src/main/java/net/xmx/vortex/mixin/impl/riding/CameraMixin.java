package net.xmx.vortex.mixin.impl.riding;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.xmx.vortex.physics.object.physicsobject.client.ClientObjectDataManager;
import net.xmx.vortex.physics.object.physicsobject.client.interpolation.InterpolationFrame;
import net.xmx.vortex.physics.object.physicsobject.client.interpolation.RenderState;
import net.xmx.vortex.physics.object.riding.RidingProxyEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow private boolean initialized;
    @Shadow private BlockGetter level;
    @Shadow private boolean detached;
    @Shadow private Entity entity;
    @Shadow private float eyeHeight;
    @Shadow private float eyeHeightOld;
    @Shadow private float yRot;
    @Shadow private float xRot;

    @Shadow protected abstract void setRotation(float yRot, float xRot);
    @Shadow protected abstract void setPosition(double x, double y, double z);
    @Shadow protected abstract void move(double distanceOffset, double verticalOffset, double horizontalOffset);
    @Shadow protected abstract double getMaxZoom(double startingDistance);

    private static final RenderState vortex_reusableRenderState_camera = new RenderState();

    @Inject(method = "setup", at = @At("HEAD"), cancellable = true)
    private void vortex_followPhysicsObject(BlockGetter area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float partialTick, CallbackInfo ci) {
        if (focusedEntity.getVehicle() instanceof RidingProxyEntity proxy) {

            this.initialized = true;
            this.level = area;
            this.entity = focusedEntity;
            this.detached = thirdPerson;

            proxy.getPhysicsObjectId().ifPresent(id -> {
                InterpolationFrame frame = ClientObjectDataManager.getInstance().getInterpolationFrame(id);
                if (frame == null || !frame.isInitialized) {
                    return;
                }

                frame.interpolate(vortex_reusableRenderState_camera, partialTick);
                RVec3 physPos = vortex_reusableRenderState_camera.transform.getTranslation();
                Quat physRotQuat = vortex_reusableRenderState_camera.transform.getRotation();

                Quaternionf physRotation = new Quaternionf(physRotQuat.getX(), physRotQuat.getY(), physRotQuat.getZ(), physRotQuat.getW());

                Vector3f rideOffset = new Vector3f(proxy.getRidePositionOffset());
                physRotation.transform(rideOffset);

                double playerX = physPos.x() + rideOffset.x();
                double playerY = physPos.y() + rideOffset.y();
                double playerZ = physPos.z() + rideOffset.z();

                this.setRotation(focusedEntity.getViewYRot(partialTick), focusedEntity.getViewXRot(partialTick));

                double eyeY = Mth.lerp(partialTick, this.eyeHeightOld, this.eyeHeight);
                this.setPosition(playerX, playerY + eyeY, playerZ);

                if (thirdPerson) {
                    if (inverseView) {
                        this.setRotation(this.yRot + 180.0F, -this.xRot);
                    }
                    this.move(-this.getMaxZoom(4.0D), 0.0D, 0.0D);
                }

                ci.cancel();
            });
        }
    }
}