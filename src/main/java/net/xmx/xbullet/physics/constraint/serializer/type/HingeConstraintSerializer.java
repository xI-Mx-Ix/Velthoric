package net.xmx.xbullet.physics.constraint.serializer.type;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.enumerate.EMotorState;
import net.minecraft.nbt.CompoundTag;
import net.xmx.xbullet.physics.constraint.manager.ConstraintManager;
import net.xmx.xbullet.physics.constraint.serializer.IConstraintSerializer;
import net.xmx.xbullet.physics.constraint.util.NbtUtil;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.PhysicsObjectManager;

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
        return createFromLoadedBodies(tag, objectManager, (bodyInterface, b1Id, b2Id, t) -> {
            HingeConstraintSettings settings = new HingeConstraintSettings();
            try {
                settings.setSpace(EConstraintSpace.valueOf(t.getString("space")));
                settings.setPoint1(NbtUtil.getRVec3(t, "point1"));
                settings.setPoint2(NbtUtil.getRVec3(t, "point2"));
                settings.setHingeAxis1(NbtUtil.getVec3(t, "hingeAxis1"));
                settings.setHingeAxis2(NbtUtil.getVec3(t, "hingeAxis2"));
                settings.setNormalAxis1(NbtUtil.getVec3(t, "normalAxis1"));
                settings.setNormalAxis2(NbtUtil.getVec3(t, "normalAxis2"));
                settings.setLimitsMin(t.getFloat("limitsMin"));
                settings.setLimitsMax(t.getFloat("limitsMax"));
                settings.setMaxFrictionTorque(t.getFloat("maxFrictionTorque"));
                NbtUtil.loadMotorSettings(t, "motorSettings", settings.getMotorSettings());
                NbtUtil.loadSpringSettings(t, "limitsSpringSettings", settings.getLimitsSpringSettings());
                HingeConstraint constraint = (HingeConstraint) bodyInterface.createConstraint(settings, b1Id, b2Id);
                if (constraint != null) {
                    constraint.setTargetAngle(t.getFloat("targetAngle"));
                    constraint.setTargetAngularVelocity(t.getFloat("targetAngularVelocity"));
                    constraint.setMotorState(EMotorState.valueOf(t.getString("motorState")));
                }
                return constraint;
            } finally {
                settings.close();
            }
        });
    }
}