package net.xmx.xbullet.physics.constraint.serializer;

import com.github.stephengold.joltjni.HingeConstraint;
import com.github.stephengold.joltjni.HingeConstraintSettings;
import com.github.stephengold.joltjni.MotorSettings;
import com.github.stephengold.joltjni.SpringSettings;
import com.github.stephengold.joltjni.TwoBodyConstraint;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.enumerate.EMotorState;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.xbullet.physics.constraint.builder.HingeConstraintBuilder;
import net.xmx.xbullet.physics.constraint.serializer.base.ConstraintSerializer;
import net.xmx.xbullet.physics.constraint.util.BufferUtil;

public class HingeConstraintSerializer implements ConstraintSerializer<HingeConstraintBuilder, HingeConstraint, HingeConstraintSettings> {

    @Override
    public String getTypeId() {
        return "xbullet:hinge";
    }

    @Override
    public void serializeSettings(HingeConstraintBuilder builder, FriendlyByteBuf buf) {
        HingeConstraintSettings settings = builder.getSettings();
        buf.writeEnum(settings.getSpace());
        BufferUtil.putRVec3(buf, settings.getPoint1());
        BufferUtil.putRVec3(buf, settings.getPoint2());
        BufferUtil.putVec3(buf, settings.getHingeAxis1());
        BufferUtil.putVec3(buf, settings.getHingeAxis2());
        BufferUtil.putVec3(buf, settings.getNormalAxis1());
        BufferUtil.putVec3(buf, settings.getNormalAxis2());
        buf.writeFloat(settings.getLimitsMin());
        buf.writeFloat(settings.getLimitsMax());
        buf.writeFloat(settings.getMaxFrictionTorque());
        try (MotorSettings motor = settings.getMotorSettings()) {
            BufferUtil.putMotorSettings(buf, motor);
        }
        try (SpringSettings spring = settings.getLimitsSpringSettings()) {
            BufferUtil.putSpringSettings(buf, spring);
        }
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
        s.setLimitsMin(buf.readFloat());
        s.setLimitsMax(buf.readFloat());
        s.setMaxFrictionTorque(buf.readFloat());
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
        if (constraint instanceof HingeConstraint hinge) {
            buf.writeFloat(hinge.getTargetAngle());
            buf.writeFloat(hinge.getTargetAngularVelocity());
            buf.writeEnum(hinge.getMotorState());
            try (MotorSettings motor = hinge.getMotorSettings()) {
                BufferUtil.putMotorSettings(buf, motor);
            }
            try (SpringSettings spring = hinge.getLimitsSpringSettings()) {
                BufferUtil.putSpringSettings(buf, spring);
            }
        }
    }

    @Override
    public void applyLiveState(TwoBodyConstraint constraint, FriendlyByteBuf buf) {
        if (constraint instanceof HingeConstraint hinge) {
            hinge.setTargetAngle(buf.readFloat());
            hinge.setTargetAngularVelocity(buf.readFloat());
            hinge.setMotorState(buf.readEnum(EMotorState.class));
            try (MotorSettings motor = hinge.getMotorSettings()) {
                BufferUtil.loadMotorSettings(buf, motor);
            }
            try (SpringSettings spring = hinge.getLimitsSpringSettings()) {
                BufferUtil.loadSpringSettings(buf, spring);
            }
        }
    }
}