package net.xmx.xbullet.physics.constraint.serializer;

import com.github.stephengold.joltjni.HingeConstraint;
import com.github.stephengold.joltjni.HingeConstraintSettings;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.enumerate.EMotorState;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.xbullet.physics.constraint.builder.HingeConstraintBuilder;
import net.xmx.xbullet.physics.constraint.serializer.base.ConstraintSerializer;
import net.xmx.xbullet.physics.constraint.util.BufferUtil;

public class HingeConstraintSerializer implements ConstraintSerializer<HingeConstraintBuilder, HingeConstraint, HingeConstraintSettings> {
    @Override public String getTypeId() { return "xbullet:hinge"; }

    @Override
    public void serialize(HingeConstraintBuilder builder, FriendlyByteBuf buf) {
        serializeBodies(builder, buf);
        buf.writeEnum(builder.space);
        BufferUtil.putRVec3(buf, builder.point1);
        BufferUtil.putRVec3(buf, builder.point2);
        BufferUtil.putVec3(buf, builder.hingeAxis1);
        BufferUtil.putVec3(buf, builder.hingeAxis2);
        BufferUtil.putVec3(buf, builder.normalAxis1);
        BufferUtil.putVec3(buf, builder.normalAxis2);
        buf.writeFloat(builder.limitsMin);
        buf.writeFloat(builder.limitsMax);
        buf.writeFloat(builder.maxFrictionTorque);
        BufferUtil.putMotorSettings(buf, builder.motorSettings);
        BufferUtil.putSpringSettings(buf, builder.limitsSpringSettings);
        buf.writeFloat(0f);
        buf.writeFloat(0f);
        buf.writeEnum(EMotorState.Off);
    }

    @Override
    public HingeConstraintSettings createSettings(FriendlyByteBuf buf) {
        HingeConstraintSettings s = new HingeConstraintSettings();
        s.setSpace(buf.readEnum(EConstraintSpace.class));
        s.setPoint1(BufferUtil.getRVec3(buf));
        s.setPoint2(BufferUtil.getRVec3(buf));
        s.setHingeAxis1(BufferUtil.getVec3(buf));
        s.setHingeAxis2(BufferUtil.getVec3(buf));
        s.setNormalAxis1(BufferUtil.getVec3(buf));
        s.setNormalAxis2(BufferUtil.getVec3(buf));
        float min = buf.readFloat();
        float max = buf.readFloat();
        if (min <= max) {
            s.setLimitsMin(min);
            s.setLimitsMax(max);
        }
        s.setMaxFrictionTorque(buf.readFloat());
        BufferUtil.loadMotorSettings(buf, s.getMotorSettings());
        BufferUtil.loadSpringSettings(buf, s.getLimitsSpringSettings());
        return s;
    }

    @Override
    public void applyLiveState(HingeConstraint constraint, FriendlyByteBuf buf) {
        constraint.setTargetAngle(buf.readFloat());
        constraint.setTargetAngularVelocity(buf.readFloat());
        constraint.setMotorState(buf.readEnum(EMotorState.class));
    }
}