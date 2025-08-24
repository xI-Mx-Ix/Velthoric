package net.xmx.velthoric.mixin.impl.entity_collision;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.operator.Op;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
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

    @Shadow public Level level;
    @Shadow public abstract void setPos(double d, double e, double f);
    @Shadow public abstract net.minecraft.world.phys.Vec3 position();
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
                data.attachedBodyUuid = null;
                return;
            }

            Optional<VxAbstractBody> bodyOpt = physicsWorld.getObjectManager().getObject(data.attachedBodyUuid);
            if (bodyOpt.isEmpty()) {
                data.attachedBodyUuid = null;
                data.lastBodyTransform = null;
                return;
            }

            int bodyId = bodyOpt.get().getBodyId();
            if (bodyId == 0) {
                data.attachedBodyUuid = null;
                data.lastBodyTransform = null;
                return;
            }

            try (BodyLockRead lock = new BodyLockRead(physicsWorld.getBodyLockInterface(), bodyId)) {
                if (lock.succeededAndIsInBroadPhase() && data.lastBodyTransform != null) {
                    Body body = lock.getBody();
                    RMat44 currentTransform = body.getWorldTransform();
                    RMat44 invertedLastTransform = data.lastBodyTransform.inversed();
                    RMat44 deltaTransform = currentTransform.multiply(invertedLastTransform);

                    net.minecraft.world.phys.Vec3 entityPos = position();
                    Vec3 joltPos = new Vec3((float)entityPos.x, (float)entityPos.y, (float)entityPos.z);
                    RVec3 newJoltPos = deltaTransform.multiply3x4(joltPos);
                    setPos(newJoltPos.xx(), newJoltPos.yy(), newJoltPos.zz());

                    Quat deltaRotation = deltaTransform.getQuaternion();

                    Vec3 forward = new Vec3(0f, 0f, 1f);
                    Vec3 newForward = Op.star(deltaRotation, forward);
                    float yawChangeRad = (float)Math.atan2(-newForward.getX(), newForward.getZ());
                    float yawChange = (float) Math.toDegrees(yawChangeRad);

                    setYRot(getYRot() + yawChange);

                    data.lastBodyTransform = currentTransform;
                } else {
                    data.attachedBodyUuid = null;
                    data.lastBodyTransform = null;
                }
            }
        }
        data.ticksSinceGrounded++;
    }
}