/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.bridge.collision.entity;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.operator.Op;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.body.type.VxBody;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

/**
 * Handles the logic for moving entities that are standing on top of moving physics bodies.
 * Calculates the relative displacement and rotation based on the body's transform changes.
 *
 * @author xI-Mx-Ix
 */
public final class VxEntityDragger {

    /**
     * Updates the entity's position and rotation if it is attached to a moving body.
     *
     * @param entity The entity to tick.
     */
    public static void tick(Entity entity) {
        IVxEntityAttachmentData attachmentProvider = (IVxEntityAttachmentData) entity;
        VxEntityAttachmentData data = attachmentProvider.getAttachmentData();

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

    private static void dragEntity(Entity entity, VxEntityAttachmentData data) {
        VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(entity.level().dimension());
        if (physicsWorld == null || !physicsWorld.isRunning() || physicsWorld.getPhysicsSystem().getBodyLockInterface() == null) {
            data.detach();
            return;
        }

        VxBody body = physicsWorld.getBodyManager().getVxBody(data.attachedBodyUuid);
        if (body == null || body.getBodyId() == 0) {
            data.detach();
            return;
        }
        int bodyId = body.getBodyId();

        try (BodyLockRead lock = new BodyLockRead(physicsWorld.getPhysicsSystem().getBodyLockInterface(), bodyId)) {
            if (!lock.succeededAndIsInBroadPhase()) {
                data.detach();
                return;
            }

            var joltBody = lock.getBody();
            RMat44 currentTransform = joltBody.getWorldTransform();

            if (data.lastBodyTransform != null) {
                // Calculate ideal new position based on relative offset from previous frame
                RVec3 entityReferencePos = new RVec3(entity.xo, entity.yo, entity.zo);
                RVec3 newPosIdeal;
                try (RMat44 worldToShip_prev = data.lastBodyTransform.inversed()) {
                    RVec3 posInShip_prev = worldToShip_prev.multiply3x4(entityReferencePos);
                    newPosIdeal = currentTransform.multiply3x4(posInShip_prev);
                }

                Vec3 addedMovement = new Vec3(
                        (float) (newPosIdeal.xx() - entity.xo),
                        (float) (newPosIdeal.yy() - entity.yo),
                        (float) (newPosIdeal.zz() - entity.zo)
                );

                // Calculate rotation change
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

    /**
     * Applies decay to the movement added by the physics body when the entity steps off.
     * This prevents abrupt stops and preserves some momentum.
     */
    private static void decayDrag(Entity entity, VxEntityAttachmentData data) {
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