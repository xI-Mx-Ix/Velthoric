package net.xmx.xbullet.mixin.riding;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.operator.Op;
import net.minecraft.client.Camera;
import net.xmx.xbullet.physics.object.riding.ClientPlayerRidingSystem;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow private float yRot;
    @Shadow private float xRot;

    @Shadow private final org.joml.Vector3f forwards = new org.joml.Vector3f(0.0F, 0.0F, 1.0F);
    @Shadow private final org.joml.Vector3f up = new org.joml.Vector3f(0.0F, 1.0F, 0.0F);
    @Shadow private final org.joml.Vector3f left = new org.joml.Vector3f(1.0F, 0.0F, 0.0F);
    @Shadow private final Quaternionf rotation = new Quaternionf(0.0F, 0.0F, 0.0F, 1.0F);

    @Inject(
            method = "setRotation(FF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void xbullet_setPhysicsRotation(float yaw, float pitch, CallbackInfo ci) {
        if (ClientPlayerRidingSystem.isRiding()) {
            ClientPlayerRidingSystem.getTargetWorldRotation().ifPresent(worldRot -> {
                ci.cancel();

                Quat joltQuat = worldRot;
                this.rotation.set(joltQuat.getX(), joltQuat.getY(), joltQuat.getZ(), joltQuat.getW());

                this.forwards.set(0.0F, 0.0F, 1.0F).rotate(this.rotation);
                this.up.set(0.0F, 1.0F, 0.0F).rotate(this.rotation);
                this.left.set(1.0F, 0.0F, 0.0F).rotate(this.rotation);

                Vec3 lookVec = worldRot.rotateAxisZ();
                lookVec = Op.star(lookVec.normalized(), -1f);
                this.yRot = (float) Math.toDegrees(Math.atan2(-lookVec.getX(), lookVec.getZ()));
                this.xRot = (float) Math.toDegrees(Math.asin(lookVec.getY()));
            });
        }
    }
}