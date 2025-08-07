package net.xmx.vortex.mixin.impl.riding;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.xmx.vortex.math.VxOperations;
import net.xmx.vortex.physics.object.physicsobject.client.ClientObjectDataManager;
import net.xmx.vortex.physics.object.physicsobject.client.interpolation.InterpolatedRenderState;
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

    @Inject(method = "setup", at = @At("HEAD"), cancellable = true)
    private void vortex_followPhysicsObject(BlockGetter area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float partialTick, CallbackInfo ci) {
        if (focusedEntity.getVehicle() instanceof RidingProxyEntity proxy) {

            this.initialized = true;
            this.level = area;
            this.entity = focusedEntity;
            this.detached = thirdPerson;

            proxy.getPhysicsObjectId().ifPresent(id -> {
                InterpolatedRenderState state = ClientObjectDataManager.getInstance().getRenderState(id);
                if (state == null || !state.isInitialized) {

                    return;
                }

                RVec3 prevPos = state.previous.transform.getTranslation();
                RVec3 currPos = state.current.transform.getTranslation();
                double physX = Mth.lerp(partialTick, prevPos.xx(), currPos.xx());
                double physY = Mth.lerp(partialTick, prevPos.yy(), currPos.yy());
                double physZ = Mth.lerp(partialTick, prevPos.zz(), currPos.zz());

                Quat prevRot = state.previous.transform.getRotation();
                Quat currRot = state.current.transform.getRotation();
                Quat physRotQuat = new Quat();
                VxOperations.slerp(prevRot, currRot, partialTick, physRotQuat);
                Quaternionf physRotation = new Quaternionf(physRotQuat.getX(), physRotQuat.getY(), physRotQuat.getZ(), physRotQuat.getW());

                Vector3f rideOffset = new Vector3f(proxy.getRidePositionOffset());
                physRotation.transform(rideOffset);
                double playerX = physX + rideOffset.x();
                double playerY = physY + rideOffset.y();
                double playerZ = physZ + rideOffset.z();

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