package net.xmx.xbullet.physics.constraint.serializer;

import com.github.stephengold.joltjni.SliderConstraint;
import com.github.stephengold.joltjni.SliderConstraintSettings;
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
    public void serialize(SliderConstraintBuilder builder, FriendlyByteBuf buf) {
        serializeBodies(builder, buf);
        buf.writeEnum(builder.space);
        BufferUtil.putRVec3(buf, builder.point1);
        BufferUtil.putRVec3(buf, builder.point2);
        BufferUtil.putVec3(buf, builder.sliderAxis1);
        BufferUtil.putVec3(buf, builder.sliderAxis2);
        BufferUtil.putVec3(buf, builder.normalAxis1);
        BufferUtil.putVec3(buf, builder.normalAxis2);
        buf.writeFloat(builder.limitsMin);
        buf.writeFloat(builder.limitsMax);
        buf.writeFloat(builder.maxFrictionForce);
        BufferUtil.putMotorSettings(buf, builder.motorSettings);
        BufferUtil.putSpringSettings(buf, builder.limitsSpringSettings);

        // Live state placeholders
        buf.writeFloat(0f); // Target Position
        buf.writeFloat(0f); // Target Velocity
        buf.writeEnum(EMotorState.Off); // Motor State
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
        float min = buf.readFloat();
        float max = buf.readFloat();
        if (min <= max) {
            s.setLimitsMin(min);
            s.setLimitsMax(max);
        }
        s.setMaxFrictionForce(buf.readFloat());
        BufferUtil.loadMotorSettings(buf, s.getMotorSettings());
        BufferUtil.loadSpringSettings(buf, s.getLimitsSpringSettings());
        return s;
    }

    @Override
    public void applyLiveState(SliderConstraint constraint, FriendlyByteBuf buf) {
        constraint.setTargetPosition(buf.readFloat());
        constraint.setTargetVelocity(buf.readFloat());
        constraint.setMotorState(buf.readEnum(EMotorState.class));
    }
}