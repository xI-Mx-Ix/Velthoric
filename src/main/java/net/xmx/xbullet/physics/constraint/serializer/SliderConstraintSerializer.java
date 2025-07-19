package net.xmx.xbullet.physics.constraint.serializer;

import com.github.stephengold.joltjni.MotorSettings;
import com.github.stephengold.joltjni.SliderConstraint;
import com.github.stephengold.joltjni.SliderConstraintSettings;
import com.github.stephengold.joltjni.SpringSettings;
import com.github.stephengold.joltjni.TwoBodyConstraint;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.enumerate.EMotorState;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.xbullet.physics.constraint.builder.SliderConstraintBuilder;
import net.xmx.xbullet.physics.constraint.serializer.base.ConstraintSerializer;
import net.xmx.xbullet.physics.constraint.util.BufferUtil;

public class SliderConstraintSerializer implements ConstraintSerializer<SliderConstraintBuilder, SliderConstraint, SliderConstraintSettings> {

    @Override
    public String getTypeId() {
        return "xbullet:slider";
    }

    @Override
    public void serializeSettings(SliderConstraintBuilder builder, FriendlyByteBuf buf) {
        SliderConstraintSettings settings = builder.getSettings();
        buf.writeEnum(settings.getSpace());
        BufferUtil.putRVec3(buf, settings.getPoint1());
        BufferUtil.putRVec3(buf, settings.getPoint2());
        BufferUtil.putVec3(buf, settings.getSliderAxis1());
        BufferUtil.putVec3(buf, settings.getSliderAxis2());
        BufferUtil.putVec3(buf, settings.getNormalAxis1());
        BufferUtil.putVec3(buf, settings.getNormalAxis2());
        buf.writeFloat(settings.getLimitsMin());
        buf.writeFloat(settings.getLimitsMax());
        buf.writeFloat(settings.getMaxFrictionForce());
        try (MotorSettings motor = settings.getMotorSettings()) {
            BufferUtil.putMotorSettings(buf, motor);
        }
        try (SpringSettings spring = settings.getLimitsSpringSettings()) {
            BufferUtil.putSpringSettings(buf, spring);
        }
    }

    @Override
    public SliderConstraintSettings createSettings(FriendlyByteBuf buf) {
        SliderConstraintSettings s = new SliderConstraintSettings();
        s.setSpace(buf.readEnum(EConstraintSpace.class));
        s.setPoint1(BufferUtil.getRVec3(buf));
        s.setPoint2(BufferUtil.getRVec3(buf));
        s.setSliderAxis1(BufferUtil.getVec3(buf));
        s.setSliderAxis2(BufferUtil.getVec3(buf));
        s.setNormalAxis1(BufferUtil.getVec3(buf));
        s.setNormalAxis2(BufferUtil.getVec3(buf));
        s.setLimitsMin(buf.readFloat());
        s.setLimitsMax(buf.readFloat());
        s.setMaxFrictionForce(buf.readFloat());
        try (MotorSettings motor = s.getMotorSettings()) {
            BufferUtil.loadMotorSettings(buf, motor);
        }
        try (SpringSettings spring = s.getLimitsSpringSettings()) {
            BufferUtil.loadSpringSettings(buf, spring);
        }
        return s;
    }

    @Override
    public void serializeLiveState(TwoBodyConstraint constraint, FriendlyByteBuf buf) {
        if (constraint instanceof SliderConstraint slider) {
            buf.writeFloat(slider.getTargetPosition());
            buf.writeFloat(slider.getTargetVelocity());
            buf.writeEnum(slider.getMotorState());
            try (MotorSettings motor = slider.getMotorSettings()) {
                BufferUtil.putMotorSettings(buf, motor);
            }
            try (SpringSettings spring = slider.getLimitsSpringSettings()) {
                BufferUtil.putSpringSettings(buf, spring);
            }
        }
    }

    @Override
    public void applyLiveState(TwoBodyConstraint constraint, FriendlyByteBuf buf) {
        if (constraint instanceof SliderConstraint slider) {
            slider.setTargetPosition(buf.readFloat());
            slider.setTargetVelocity(buf.readFloat());
            slider.setMotorState(buf.readEnum(EMotorState.class));
            try (MotorSettings motor = slider.getMotorSettings()) {
                BufferUtil.loadMotorSettings(buf, motor);
            }
            try (SpringSettings spring = slider.getLimitsSpringSettings()) {
                BufferUtil.loadSpringSettings(buf, spring);
            }
        }
    }
}