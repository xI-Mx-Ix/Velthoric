package net.xmx.velthoric.mixin.impl.entity_collision;

import com.github.stephengold.joltjni.*;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.physics.entity_collision.EntityAttachmentData;
import net.xmx.velthoric.physics.entity_collision.IEntityAttachmentData;
import net.xmx.velthoric.physics.object.VxAbstractBody;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(Entity.class)
public abstract class EntityTickMixin {

    @Shadow Level level;
    @Shadow public abstract void setPos(double d, double e, double f);
    @Shadow public abstract Vec3 position();
    @Shadow public abstract void setYRot(float f);
    @Shadow public abstract float getYRot();
    @Shadow public abstract void setXRot(float f);
    @Shadow public abstract float getXRot();

    @Inject(method = "baseTick", at = @At("HEAD"))
    private void onBaseTickStart(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        IEntityAttachmentData attachmentProvider = (IEntityAttachmentData) self;
        EntityAttachmentData data = attachmentProvider.getAttachmentData();

        if (data.isAttached()) {
            VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(level.dimension());
            if (physicsWorld == null || !physicsWorld.isRunning() || physicsWorld.getBodyLockInterface() == null) {
                data.detach();
                return;
            }

            Optional<VxAbstractBody> bodyOpt = physicsWorld.getObjectManager().getObject(data.attachedBodyUuid);
            if (bodyOpt.isEmpty()) {
                data.detach();
                return;
            }

            int bodyId = bodyOpt.get().getBodyId();
            if (bodyId == 0) {
                data.detach();
                return;
            }

            try (BodyLockRead lock = new BodyLockRead(physicsWorld.getBodyLockInterface(), bodyId)) {
                if (lock.succeededAndIsInBroadPhase() && data.lastBodyTransform != null) {
                    Body body = lock.getBody();
                    RMat44 currentTransform = body.getWorldTransform();

                    RMat44 invertedLastTransform = data.lastBodyTransform.inversed();
                    RMat44 deltaTransform = currentTransform.multiply(invertedLastTransform);

                    Vec3 entityPos = position();
                    com.github.stephengold.joltjni.Vec3 joltPos = new com.github.stephengold.joltjni.Vec3((float)entityPos.x, (float)entityPos.y, (float)entityPos.z);
                    RVec3 newJoltPos = deltaTransform.multiply3x4(joltPos);
                    setPos(newJoltPos.x(), newJoltPos.y(), newJoltPos.z());

                    Quat deltaRotation = deltaTransform.getQuaternion();

                    float currentYawRad = (float) Math.toRadians(getYRot());
                    com.github.stephengold.joltjni.Vec3 yawOnlyVec = new com.github.stephengold.joltjni.Vec3(
                            -Mth.sin(currentYawRad), 0, Mth.cos(currentYawRad));

                    yawOnlyVec.rotateInPlace(deltaRotation);

                    float newYaw = (float) Math.toDegrees(Mth.atan2(-yawOnlyVec.getX(), yawOnlyVec.getZ()));

                    float yawChange = Mth.wrapDegrees(newYaw - getYRot());

                    setYRot(getYRot() + yawChange);

                    data.lastBodyTransform = currentTransform;
                } else {
                    if (lock.succeededAndIsInBroadPhase()) {
                        data.lastBodyTransform = lock.getBody().getWorldTransform();
                    } else {
                        data.detach();
                    }
                }
            }
        }
        data.ticksSinceGrounded++;
    }
}