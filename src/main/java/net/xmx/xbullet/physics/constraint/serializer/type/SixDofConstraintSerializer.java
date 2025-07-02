package net.xmx.xbullet.physics.constraint.serializer.type;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EAxis;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.enumerate.EMotorState;
import com.github.stephengold.joltjni.enumerate.ESwingType;
import net.minecraft.nbt.CompoundTag;
import net.xmx.xbullet.physics.constraint.manager.ConstraintManager;
import net.xmx.xbullet.physics.constraint.serializer.IConstraintSerializer;
import net.xmx.xbullet.physics.constraint.util.NbtUtil;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.PhysicsObjectManager;

import java.util.concurrent.CompletableFuture;

public class SixDofConstraintSerializer implements IConstraintSerializer<SixDofConstraint> {

    @Override
    public void save(SixDofConstraint constraint, CompoundTag tag) {
        try (SixDofConstraintSettings settings = (SixDofConstraintSettings) constraint.getConstraintSettings().getPtr()) {
            tag.putString("space", settings.getSpace().name());
            tag.putString("swingType", settings.getSwingType().name());
            NbtUtil.putRVec3(tag, "position1", settings.getPosition1());
            NbtUtil.putRVec3(tag, "position2", settings.getPosition2());
            NbtUtil.putVec3(tag, "axisX1", settings.getAxisX1());
            NbtUtil.putVec3(tag, "axisY1", settings.getAxisY1());
            NbtUtil.putVec3(tag, "axisX2", settings.getAxisX2());
            NbtUtil.putVec3(tag, "axisY2", settings.getAxisY2());

            CompoundTag axesTag = new CompoundTag();
            for (EAxis axis : EAxis.values()) {
                CompoundTag axisTag = new CompoundTag();
                axisTag.putBoolean("isFixed", settings.isFixedAxis(axis));
                axisTag.putFloat("limitMin", settings.getLimitMin(axis));
                axisTag.putFloat("limitMax", settings.getLimitMax(axis));
                axisTag.putFloat("maxFriction", settings.getMaxFriction(axis));
                NbtUtil.putMotorSettings(axisTag, "motorSettings", settings.getMotorSettings(axis));
                NbtUtil.putSpringSettings(axisTag, "limitsSpringSettings", settings.getLimitsSpringSettings(axis));
                axisTag.putString("motorState", constraint.getMotorState(axis).name());
                axesTag.put(axis.name(), axisTag);
            }
            tag.put("axes", axesTag);

            NbtUtil.putVec3(tag, "targetPositionCs", constraint.getTargetPositionCs());
            NbtUtil.putQuat(tag, "targetOrientationCs", constraint.getTargetOrientationCs());
            NbtUtil.putVec3(tag, "targetVelocityCs", constraint.getTargetVelocityCs());
            NbtUtil.putVec3(tag, "targetAngularVelocityCs", constraint.getTargetAngularVelocityCs());
        }
    }

    @Override
    public CompletableFuture<TwoBodyConstraint> createAndLink(CompoundTag tag, ConstraintManager constraintManager, PhysicsObjectManager objectManager) {
        return createFromLoadedBodies(tag, objectManager, (b1, b2, t) -> {
            try (SixDofConstraintSettings settings = new SixDofConstraintSettings()) {
                settings.setSpace(EConstraintSpace.valueOf(t.getString("space")));
                settings.setSwingType(ESwingType.valueOf(t.getString("swingType")));
                settings.setPosition1(NbtUtil.getRVec3(t, "position1"));
                settings.setPosition2(NbtUtil.getRVec3(t, "position2"));
                settings.setAxisX1(NbtUtil.getVec3(t, "axisX1"));
                settings.setAxisY1(NbtUtil.getVec3(t, "axisY1"));
                settings.setAxisX2(NbtUtil.getVec3(t, "axisX2"));
                settings.setAxisY2(NbtUtil.getVec3(t, "axisY2"));

                CompoundTag axesTag = t.getCompound("axes");
                for (EAxis axis : EAxis.values()) {
                    CompoundTag axisTag = axesTag.getCompound(axis.name());
                    if (axisTag.getBoolean("isFixed")) {
                        settings.makeFixedAxis(axis);
                    } else {
                        settings.setLimitedAxis(axis, axisTag.getFloat("limitMin"), axisTag.getFloat("limitMax"));
                    }
                    settings.setMaxFriction(axis, axisTag.getFloat("maxFriction"));
                    NbtUtil.loadMotorSettings(axisTag, "motorSettings", settings.getMotorSettings(axis));
                    NbtUtil.loadSpringSettings(axisTag, "limitsSpringSettings", settings.getLimitsSpringSettings(axis));
                }

                SixDofConstraint constraint = (SixDofConstraint) settings.create(b1, b2);
                if (constraint != null) {
                    CompoundTag axesTagRuntime = t.getCompound("axes");
                    for (EAxis axis : EAxis.values()) {
                        CompoundTag axisTag = axesTagRuntime.getCompound(axis.name());
                        constraint.setMotorState(axis, EMotorState.valueOf(axisTag.getString("motorState")));
                    }
                    constraint.setTargetPositionCs(NbtUtil.getVec3(t, "targetPositionCs"));
                    constraint.setTargetOrientationCs(NbtUtil.getQuat(t, "targetOrientationCs"));
                    constraint.setTargetVelocityCs(NbtUtil.getVec3(t, "targetVelocityCs"));
                    constraint.setTargetAngularVelocityCs(NbtUtil.getVec3(t, "targetAngularVelocityCs"));
                }
                return constraint;
            }
        });
    }
}