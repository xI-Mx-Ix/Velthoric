package net.xmx.xbullet.physics.constraint.serializer.type;

import com.github.stephengold.joltjni.SliderConstraint;
import com.github.stephengold.joltjni.SliderConstraintSettings;
import com.github.stephengold.joltjni.TwoBodyConstraint;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.enumerate.EMotorState;
import net.minecraft.nbt.CompoundTag;
import net.xmx.xbullet.physics.constraint.manager.ConstraintManager;
import net.xmx.xbullet.physics.constraint.serializer.IConstraintSerializer;
import net.xmx.xbullet.physics.constraint.util.NbtUtil;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.PhysicsObjectManager;

import java.util.concurrent.CompletableFuture;

public class SliderConstraintSerializer implements IConstraintSerializer<SliderConstraint> {

    @Override
    public void save(SliderConstraint constraint, CompoundTag tag) {
        try (SliderConstraintSettings settings = (SliderConstraintSettings) constraint.getConstraintSettings().getPtr()) {
            tag.putString("space", settings.getSpace().name());
            NbtUtil.putRVec3(tag, "point1", settings.getPoint1());
            NbtUtil.putRVec3(tag, "point2", settings.getPoint2());
            NbtUtil.putVec3(tag, "sliderAxis1", settings.getSliderAxis1());
            NbtUtil.putVec3(tag, "sliderAxis2", settings.getSliderAxis2());
            NbtUtil.putVec3(tag, "normalAxis1", settings.getNormalAxis1());
            NbtUtil.putVec3(tag, "normalAxis2", settings.getNormalAxis2());
            tag.putFloat("limitsMin", settings.getLimitsMin());
            tag.putFloat("limitsMax", settings.getLimitsMax());
            tag.putFloat("maxFrictionForce", settings.getMaxFrictionForce());
            NbtUtil.putMotorSettings(tag, "motorSettings", settings.getMotorSettings());
            NbtUtil.putSpringSettings(tag, "limitsSpringSettings", settings.getLimitsSpringSettings());
            tag.putFloat("targetPosition", constraint.getTargetPosition());
            tag.putFloat("targetVelocity", constraint.getTargetVelocity());
            tag.putString("motorState", constraint.getMotorState().name());
        }
    }

    @Override
    public CompletableFuture<TwoBodyConstraint> createAndLink(CompoundTag tag, ConstraintManager constraintManager, PhysicsObjectManager objectManager) {
        return createFromLoadedBodies(tag, objectManager, (b1, b2, t) -> {
            try (SliderConstraintSettings settings = new SliderConstraintSettings()) {
                settings.setSpace(EConstraintSpace.valueOf(t.getString("space")));
                settings.setPoint1(NbtUtil.getRVec3(t, "point1"));
                settings.setPoint2(NbtUtil.getRVec3(t, "point2"));
                settings.setSliderAxis1(NbtUtil.getVec3(t, "sliderAxis1"));
                settings.setSliderAxis2(NbtUtil.getVec3(t, "sliderAxis2"));
                settings.setNormalAxis1(NbtUtil.getVec3(t, "normalAxis1"));
                settings.setNormalAxis2(NbtUtil.getVec3(t, "normalAxis2"));
                settings.setLimitsMin(t.getFloat("limitsMin"));
                settings.setLimitsMax(t.getFloat("limitsMax"));
                settings.setMaxFrictionForce(t.getFloat("maxFrictionForce"));
                NbtUtil.loadMotorSettings(t, "motorSettings", settings.getMotorSettings());
                NbtUtil.loadSpringSettings(t, "limitsSpringSettings", settings.getLimitsSpringSettings());
                SliderConstraint constraint = (SliderConstraint) settings.create(b1, b2);
                if (constraint != null) {
                    constraint.setTargetPosition(t.getFloat("targetPosition"));
                    constraint.setTargetVelocity(t.getFloat("targetVelocity"));
                    constraint.setMotorState(EMotorState.valueOf(t.getString("motorState")));
                }
                return constraint;
            }
        });
    }
}