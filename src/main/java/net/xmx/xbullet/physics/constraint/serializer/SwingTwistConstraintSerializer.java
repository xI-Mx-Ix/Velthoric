package net.xmx.xbullet.physics.constraint.serializer;

import com.github.stephengold.joltjni.MotorSettings;
import com.github.stephengold.joltjni.SwingTwistConstraint;
import com.github.stephengold.joltjni.SwingTwistConstraintSettings;
import com.github.stephengold.joltjni.TwoBodyConstraint;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.enumerate.EMotorState;
import com.github.stephengold.joltjni.enumerate.ESwingType;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.xbullet.physics.constraint.builder.SwingTwistConstraintBuilder;
import net.xmx.xbullet.physics.constraint.serializer.base.ConstraintSerializer;
import net.xmx.xbullet.physics.constraint.util.BufferUtil;

public class SwingTwistConstraintSerializer implements ConstraintSerializer<SwingTwistConstraintBuilder, SwingTwistConstraint, SwingTwistConstraintSettings> {

    @Override
    public String getTypeId() {
        return "xbullet:swing_twist";
    }

    @Override
    public void serializeSettings(SwingTwistConstraintBuilder builder, FriendlyByteBuf buf) {
        SwingTwistConstraintSettings settings = builder.getSettings();
        buf.writeEnum(settings.getSpace());
        buf.writeEnum(settings.getSwingType());
        BufferUtil.putRVec3(buf, settings.getPosition1());
        BufferUtil.putRVec3(buf, settings.getPosition2());
        BufferUtil.putVec3(buf, settings.getTwistAxis1());
        BufferUtil.putVec3(buf, settings.getTwistAxis2());
        BufferUtil.putVec3(buf, settings.getPlaneAxis1());
        BufferUtil.putVec3(buf, settings.getPlaneAxis2());
        buf.writeFloat(settings.getNormalHalfConeAngle());
        buf.writeFloat(settings.getPlaneHalfConeAngle());
        buf.writeFloat(settings.getTwistMinAngle());
        buf.writeFloat(settings.getTwistMaxAngle());
        buf.writeFloat(settings.getMaxFrictionTorque());
        try (MotorSettings motor = settings.getSwingMotorSettings()) {
            BufferUtil.putMotorSettings(buf, motor);
        }
        try (MotorSettings motor = settings.getTwistMotorSettings()) {
            BufferUtil.putMotorSettings(buf, motor);
        }
    }

    @Override
    public SwingTwistConstraintSettings createSettings(FriendlyByteBuf buf) {
        SwingTwistConstraintSettings s = new SwingTwistConstraintSettings();
        s.setSpace(buf.readEnum(EConstraintSpace.class));
        s.setSwingType(buf.readEnum(ESwingType.class));
        s.setPosition1(BufferUtil.getRVec3(buf));
        s.setPosition2(BufferUtil.getRVec3(buf));
        s.setTwistAxis1(BufferUtil.getVec3(buf));
        s.setTwistAxis2(BufferUtil.getVec3(buf));
        s.setPlaneAxis1(BufferUtil.getVec3(buf));
        s.setPlaneAxis2(BufferUtil.getVec3(buf));
        s.setNormalHalfConeAngle(buf.readFloat());
        s.setPlaneHalfConeAngle(buf.readFloat());
        s.setTwistMinAngle(buf.readFloat());
        s.setTwistMaxAngle(buf.readFloat());
        s.setMaxFrictionTorque(buf.readFloat());
        try (MotorSettings motor = s.getSwingMotorSettings()) {
            BufferUtil.loadMotorSettings(buf, motor);
        }
        try (MotorSettings motor = s.getTwistMotorSettings()) {
            BufferUtil.loadMotorSettings(buf, motor);
        }
        return s;
    }

    @Override
    public void serializeLiveState(TwoBodyConstraint constraint, FriendlyByteBuf buf) {
        if (constraint instanceof SwingTwistConstraint st) {

            buf.writeEnum(EMotorState.Off);
            buf.writeEnum(EMotorState.Off);

            try (MotorSettings swingMotor = st.getSwingMotorSettings()) {
                BufferUtil.putMotorSettings(buf, swingMotor);
            }
            try (MotorSettings twistMotor = st.getTwistMotorSettings()) {
                BufferUtil.putMotorSettings(buf, twistMotor);
            }
        }
    }

    @Override
    public void applyLiveState(TwoBodyConstraint constraint, FriendlyByteBuf buf) {
        if (constraint instanceof SwingTwistConstraint st) {
            st.setSwingMotorState(buf.readEnum(EMotorState.class));
            st.setTwistMotorState(buf.readEnum(EMotorState.class));

            try (MotorSettings swingMotor = st.getSwingMotorSettings()) {
                BufferUtil.loadMotorSettings(buf, swingMotor);
            }
            try (MotorSettings twistMotor = st.getTwistMotorSettings()) {
                BufferUtil.loadMotorSettings(buf, twistMotor);
            }
        }
    }
}