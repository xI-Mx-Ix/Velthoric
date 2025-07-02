package net.xmx.xbullet.physics.constraint.serializer.type;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.enumerate.EMotorState;
import net.minecraft.nbt.CompoundTag;
import net.xmx.xbullet.physics.constraint.manager.ConstraintManager;
import net.xmx.xbullet.physics.constraint.serializer.IConstraintSerializer;
import net.xmx.xbullet.physics.constraint.util.NbtUtil;
import net.xmx.xbullet.physics.object.global.physicsobject.IPhysicsObject;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.PhysicsObjectManager;
import net.xmx.xbullet.physics.physicsworld.PhysicsWorld;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class HingeConstraintSerializer implements IConstraintSerializer<HingeConstraint> {

    @Override
    public void save(HingeConstraint constraint, CompoundTag tag) {
        try (HingeConstraintSettings settings = (HingeConstraintSettings) constraint.getConstraintSettings().getPtr()) {
            tag.putString("space", settings.getSpace().name());
            NbtUtil.putRVec3(tag, "point1", settings.getPoint1());
            NbtUtil.putRVec3(tag, "point2", settings.getPoint2());
            NbtUtil.putVec3(tag, "hingeAxis1", settings.getHingeAxis1());
            NbtUtil.putVec3(tag, "hingeAxis2", settings.getHingeAxis2());
            NbtUtil.putVec3(tag, "normalAxis1", settings.getNormalAxis1());
            NbtUtil.putVec3(tag, "normalAxis2", settings.getNormalAxis2());
            tag.putFloat("limitsMin", settings.getLimitsMin());
            tag.putFloat("limitsMax", settings.getLimitsMax());
            tag.putFloat("maxFrictionTorque", settings.getMaxFrictionTorque());
            NbtUtil.putMotorSettings(tag, "motorSettings", settings.getMotorSettings());
            NbtUtil.putSpringSettings(tag, "limitsSpringSettings", settings.getLimitsSpringSettings());
            tag.putFloat("targetAngle", constraint.getTargetAngle());
            tag.putFloat("targetAngularVelocity", constraint.getTargetAngularVelocity());
            tag.putString("motorState", constraint.getMotorState().name());
        }
    }

    @Override
    public CompletableFuture<TwoBodyConstraint> createAndLink(CompoundTag tag, ConstraintManager constraintManager, PhysicsObjectManager objectManager) {
        UUID[] bodyIds = loadBodyIds(tag);
        if (bodyIds == null) return CompletableFuture.completedFuture(null);

        CompletableFuture<IPhysicsObject> future1 = objectManager.getOrLoadObject(bodyIds[0]);
        CompletableFuture<IPhysicsObject> future2 = objectManager.getOrLoadObject(bodyIds[1]);
        PhysicsWorld physicsWorld = objectManager.getPhysicsWorld();

        return CompletableFuture.allOf(future1, future2).thenApplyAsync(v -> {
            IPhysicsObject obj1 = future1.join();
            IPhysicsObject obj2 = future2.join();
            if (obj1 == null || obj2 == null || physicsWorld == null) return null;

            int bodyId1 = obj1.getBodyId();
            int bodyId2 = obj2.getBodyId();

            BodyInterface bodyInterface = physicsWorld.getBodyInterface();
            if (bodyInterface == null) return null;

            Body b1 = new Body(bodyId1);
            Body b2 = (bodyInterface.getMotionType(bodyId2) == EMotionType.Static) ? Body.sFixedToWorld() : new Body(bodyId2);

            try (HingeConstraintSettings settings = new HingeConstraintSettings()) {
                settings.setSpace(EConstraintSpace.valueOf(tag.getString("space")));
                settings.setPoint1(NbtUtil.getRVec3(tag, "point1"));
                settings.setPoint2(NbtUtil.getRVec3(tag, "point2"));
                settings.setHingeAxis1(NbtUtil.getVec3(tag, "hingeAxis1"));
                settings.setHingeAxis2(NbtUtil.getVec3(tag, "hingeAxis2"));
                settings.setNormalAxis1(NbtUtil.getVec3(tag, "normalAxis1"));
                settings.setNormalAxis2(NbtUtil.getVec3(tag, "normalAxis2"));
                settings.setLimitsMin(tag.getFloat("limitsMin"));
                settings.setLimitsMax(tag.getFloat("limitsMax"));
                settings.setMaxFrictionTorque(tag.getFloat("maxFrictionTorque"));
                NbtUtil.loadMotorSettings(tag, "motorSettings", settings.getMotorSettings());
                NbtUtil.loadSpringSettings(tag, "limitsSpringSettings", settings.getLimitsSpringSettings());

                HingeConstraint constraint = (HingeConstraint) settings.create(b1, b2);

                if (constraint != null) {
                    constraint.setTargetAngle(tag.getFloat("targetAngle"));
                    constraint.setTargetAngularVelocity(tag.getFloat("targetAngularVelocity"));
                    constraint.setMotorState(EMotorState.valueOf(tag.getString("motorState")));
                }
                return constraint;
            }
        });
    }
}