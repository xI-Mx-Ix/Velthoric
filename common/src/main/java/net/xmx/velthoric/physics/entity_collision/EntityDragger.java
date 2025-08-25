package net.xmx.velthoric.physics.entity_collision;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.BodyLockRead;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RMat44;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.operator.Op;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.object.VxAbstractBody;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.Optional;

public final class EntityDragger {

    public static void tick(Entity entity) {
        IEntityAttachmentData attachmentProvider = (IEntityAttachmentData) entity;
        EntityAttachmentData data = attachmentProvider.getAttachmentData();

        if (data.isAttached()) {
            dragEntity(entity, data);
        } else {
            decayDrag(entity, data);
        }

        if (!entity.onGround()) {
            data.ticksSinceGrounded++;
        } else {
            data.ticksSinceGrounded = 0;

            if (data.attachedBodyUuid == null) {
                data.addedMovementLastTick.set(0f, 0f, 0f);
                data.addedYawRotLastTick = 0f;
            }
        }
    }

    private static void dragEntity(Entity entity, EntityAttachmentData data) {
        VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(entity.level().dimension());
        if (physicsWorld == null || !physicsWorld.isRunning() || physicsWorld.getBodyLockInterface() == null) {
            data.detach();
            return;
        }

        Optional<VxAbstractBody> bodyOpt = physicsWorld.getObjectManager().getObject(data.attachedBodyUuid);
        if (bodyOpt.isEmpty() || bodyOpt.get().getBodyId() == 0) {
            data.detach();
            return;
        }
        int bodyId = bodyOpt.get().getBodyId();

        try (BodyLockRead lock = new BodyLockRead(physicsWorld.getBodyLockInterface(), bodyId)) {
            if (!lock.succeededAndIsInBroadPhase()) {
                data.detach();
                return;
            }

            Body body = lock.getBody();
            RMat44 currentTransform = body.getWorldTransform();

            if (data.lastBodyTransform != null) {
                RVec3 entityReferencePos = new RVec3(entity.xo, entity.yo, entity.zo);
                RVec3 newPosIdeal;
                try (RMat44 worldToShip_prev = data.lastBodyTransform.inversed()) {
                    RVec3 posInShip_prev = worldToShip_prev.multiply3x4(entityReferencePos);
                    newPosIdeal = currentTransform.multiply3x4(posInShip_prev);
                }

                Vec3 addedMovement = new Vec3(
                        (float)(newPosIdeal.xx() - entity.xo),
                        (float)(newPosIdeal.yy() - entity.yo),
                        (float)(newPosIdeal.zz() - entity.zo)
                );

                float yViewRot = entity.yRotO;
                Vec3 entityLookYawOnly = new Vec3(
                        (float) Math.sin(-Math.toRadians(yViewRot)), 0.0f, (float) Math.cos(-Math.toRadians(yViewRot))
                );

                try (RMat44 worldToShip_prev = data.lastBodyTransform.inversed()) {
                    Quat worldToShipRot = worldToShip_prev.getQuaternion();
                    Quat shipToWorldRot = currentTransform.getQuaternion();

                    entityLookYawOnly.rotateInPlace(worldToShipRot);
                    entityLookYawOnly.rotateInPlace(shipToWorldRot);
                }

                float newYRot = (float) -Math.toDegrees(Math.atan2(entityLookYawOnly.getX(), entityLookYawOnly.getZ()));
                float addedYRot = Mth.wrapDegrees(newYRot - yViewRot);

                if (addedMovement.isFinite() && Float.isFinite(addedYRot)) {
                    entity.setPos(entity.getX() + addedMovement.getX(), entity.getY() + addedMovement.getY(), entity.getZ() + addedMovement.getZ());
                    entity.setYRot(entity.getYRot() + addedYRot);
                    entity.setYHeadRot(entity.getYHeadRot() + addedYRot);

                    data.addedMovementLastTick = addedMovement;
                    data.addedYawRotLastTick = addedYRot;
                }
            }

            if (data.lastBodyTransform != null) {
                data.lastBodyTransform.close();
            }
            data.lastBodyTransform = currentTransform;

        } catch (Exception e) {
            VxMainClass.LOGGER.error("Error during entity drag physics", e);
            data.detach();
        }
    }

    private static void decayDrag(Entity entity, EntityAttachmentData data) {
        final float decayFactor = 0.9f;
        if (data.addedMovementLastTick.lengthSq() > 1.0E-8) {
            Vec3 decayedMovement = Op.star(decayFactor, data.addedMovementLastTick);
            entity.setPos(entity.getX() + decayedMovement.getX(), entity.getY() + decayedMovement.getY(), entity.getZ() + decayedMovement.getZ());
            data.addedMovementLastTick = decayedMovement;
        } else {
            data.addedMovementLastTick.set(0f, 0f, 0f);
        }

        if (Math.abs(data.addedYawRotLastTick) > 1.0E-5) {
            float decayedRot = data.addedYawRotLastTick * decayFactor;
            entity.setYRot(entity.getYRot() + decayedRot);
            entity.setYHeadRot(entity.getYHeadRot() + decayedRot);
            data.addedYawRotLastTick = decayedRot;
        } else {
            data.addedYawRotLastTick = 0f;
        }
    }
}