package net.xmx.vortex.mixin.impl.riding;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.operator.Op;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.xmx.vortex.math.VxTransform;
import net.xmx.vortex.physics.object.physicsobject.client.ClientObjectDataManager;
import net.xmx.vortex.physics.object.physicsobject.client.interpolation.RenderData;
import net.xmx.vortex.physics.object.riding.ClientPlayerRidingSystem;
import net.xmx.vortex.physics.object.riding.PlayerRidingAttachment;
import net.xmx.vortex.physics.object.riding.RidingProxyEntity;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.UUID;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow private boolean initialized;
    @Shadow private BlockGetter level;
    @Shadow private Entity entity;
    @Shadow private final Quaternionf rotation = new Quaternionf();
    @Shadow private float xRot;
    @Shadow private float yRot;
    @Shadow private float eyeHeight;
    @Shadow private float eyeHeightOld;
    @Shadow private boolean detached;
    @Shadow private final Vector3f forwards = new Vector3f();
    @Shadow private final Vector3f up = new Vector3f();
    @Shadow private final Vector3f left = new Vector3f();

    @Shadow protected abstract void setPosition(double x, double y, double z);
    @Shadow protected abstract void move(double distanceOffset, double verticalOffset, double horizontalOffset);
    @Shadow protected abstract double getMaxZoom(double startingDistance);

    @Inject(method = "setup", at = @At("HEAD"), cancellable = true)
    private void vortex_setupPhysicsRidingCamera(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse, float partialTicks, CallbackInfo ci) {
        PlayerRidingAttachment attachment = ClientPlayerRidingSystem.getAttachment(entity);
        if (attachment == null || !attachment.isRiding()) {
            return;
        }

        RidingProxyEntity proxy = attachment.getCurrentProxy();
        if (proxy == null) return;

        Optional<UUID> physicsIdOpt = proxy.getPhysicsObjectId();
        if (physicsIdOpt.isEmpty()) return;

        RenderData renderData = ClientObjectDataManager.getInstance().getRenderData(physicsIdOpt.get(), partialTicks);
        if (renderData == null) return;

        ci.cancel();

        VxTransform renderTransform = renderData.transform;
        this.initialized = true;
        this.level = level;
        this.entity = entity;
        this.detached = detached;

        RVec3 worldPosJolt = renderTransform.toRMat44().multiply3x4(attachment.localPositionOnObject);
        Vector3d worldPos = new Vector3d(worldPosJolt.xx(), worldPosJolt.yy(), worldPosJolt.zz());

        double interpolatedEyeHeight = Mth.lerp(partialTicks, this.eyeHeightOld, this.eyeHeight);
        com.github.stephengold.joltjni.Quat vehicleRot = renderTransform.getRotation();

        Vector3f eyeOffset = new Quaternionf(vehicleRot.getX(), vehicleRot.getY(), vehicleRot.getZ(), vehicleRot.getW())
                .transform(new Vector3f(0.0f, (float)interpolatedEyeHeight, 0.0f));

        this.setPosition(worldPos.x + eyeOffset.x, worldPos.y + eyeOffset.y, worldPos.z + eyeOffset.z);

        com.github.stephengold.joltjni.Quat playerInputRot = Quat.sEulerAngles(
                (float) Math.toRadians(entity.getViewXRot(partialTicks)),
                (float) Math.toRadians(entity.getViewYRot(partialTicks)),
                0
        );

        com.github.stephengold.joltjni.Quat finalWorldRot = Op.star(vehicleRot, playerInputRot);
        this.rotation.set(finalWorldRot.getX(), finalWorldRot.getY(), finalWorldRot.getZ(), finalWorldRot.getW());

        this.xRot = entity.getViewXRot(partialTicks);
        this.yRot = entity.getViewYRot(partialTicks);

        this.forwards.set(0.0F, 0.0F, 1.0F).rotate(this.rotation);
        this.up.set(0.0F, 1.0F, 0.0F).rotate(this.rotation);
        this.left.set(1.0F, 0.0F, 0.0F).rotate(this.rotation);

        if (detached) {
            if (thirdPersonReverse) {
                float newYaw = this.yRot + 180.0f;
                float newPitch = -this.xRot;
                this.xRot = newPitch;
                this.yRot = newYaw;
                this.rotation.rotationYXZ(-newYaw * ((float)Math.PI / 180F), newPitch * ((float)Math.PI / 180F), 0.0F);

                this.forwards.set(0.0F, 0.0F, 1.0F).rotate(this.rotation);
                this.up.set(0.0F, 1.0F, 0.0F).rotate(this.rotation);
                this.left.set(1.0F, 0.0F, 0.0F).rotate(this.rotation);
            }

            double zoomDistance = 4.0;
            this.move(-this.getMaxZoom(zoomDistance), 0.0, 0.0);
        }
    }
}