package net.xmx.vortex.physics.constraint.serializer;

import com.github.stephengold.joltjni.MotorSettings;
import com.github.stephengold.joltjni.SwingTwistConstraint;
import com.github.stephengold.joltjni.SwingTwistConstraintSettings;
import com.github.stephengold.joltjni.TwoBodyConstraint;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.enumerate.EMotorState;
import com.github.stephengold.joltjni.enumerate.ESwingType;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.vortex.physics.constraint.builder.SwingTwistConstraintBuilder;
import net.xmx.vortex.physics.constraint.serializer.base.ConstraintSerializer;
import net.xmx.vortex.physics.constraint.util.VxBufferUtil;

public class SwingTwistConstraintSerializer implements ConstraintSerializer<SwingTwistConstraintBuilder, SwingTwistConstraint, SwingTwistConstraintSettings> {

    @Override
    public String getTypeId() {
        return "vortex:swing_twist";
    }

    @Override
    public void serializeSettings(SwingTwistConstraintBuilder builder, FriendlyByteBuf buf) {
        SwingTwistConstraintSettings settings = builder.getSettings();
        buf.writeEnum(settings.getSpace());
        buf.writeEnum(settings.getSwingType());
        VxBufferUtil.putRVec3(buf, settings.getPosition1());
        VxBufferUtil.putRVec3(buf, settings.getPosition2());
        VxBufferUtil.putVec3(buf, settings.getTwistAxis1());
        VxBufferUtil.putVec3(buf, settings.getTwistAxis2());
        VxBufferUtil.putVec3(buf, settings.getPlaneAxis1());
        VxBufferUtil.putVec3(buf, settings.getPlaneAxis2());
        buf.writeFloat(settings.getNormalHalfConeAngle());
        buf.writeFloat(settings.getPlaneHalfConeAngle());
        buf.writeFloat(settings.getTwistMinAngle());
        buf.writeFloat(settings.getTwistMaxAngle());
        buf.writeFloat(settings.getMaxFrictionTorque());
        try (MotorSettings motor = settings.getSwingMotorSettings()) {
            VxBufferUtil.putMotorSettings(buf, motor);
        }
        try (MotorSettings motor = settings.getTwistMotorSettings()) {
            VxBufferUtil.putMotorSettings(buf, motor);
        }
    }

    @Override
    public SwingTwistConstraintSettings createSettings(FriendlyByteBuf buf) {
        SwingTwistConstraintSettings s = new SwingTwistConstraintSettings();
        s.setSpace(buf.readEnum(EConstraintSpace.class));
        s.setSwingType(buf.readEnum(ESwingType.class));
        s.setPosition1(VxBufferUtil.getRVec3(buf));
        s.setPosition2(VxBufferUtil.getRVec3(buf));
        s.setTwistAxis1(VxBufferUtil.getVec3(buf));
        s.setTwistAxis2(VxBufferUtil.getVec3(buf));
        s.setPlaneAxis1(VxBufferUtil.getVec3(buf));
        s.setPlaneAxis2(VxBufferUtil.getVec3(buf));
        s.setNormalHalfConeAngle(buf.readFloat());
        s.setPlaneHalfConeAngle(buf.readFloat());
        s.setTwistMinAngle(buf.readFloat());
        s.setTwistMaxAngle(buf.readFloat());
        s.setMaxFrictionTorque(buf.readFloat());
        try (MotorSettings motor = s.getSwingMotorSettings()) {
            VxBufferUtil.loadMotorSettings(buf, motor);
        }
        try (MotorSettings motor = s.getTwistMotorSettings()) {
            VxBufferUtil.loadMotorSettings(buf, motor);
        }
        return s;
    }

    @Override
    public void serializeLiveState(TwoBodyConstraint constraint, FriendlyByteBuf buf) {
        if (constraint instanceof SwingTwistConstraint st) {

            buf.writeEnum(EMotorState.Off);
            buf.writeEnum(EMotorState.Off);

            try (MotorSettings swingMotor = st.getSwingMotorSettings()) {
                VxBufferUtil.putMotorSettings(buf, swingMotor);
            }
            try (MotorSettings twistMotor = st.getTwistMotorSettings()) {
                VxBufferUtil.putMotorSettings(buf, twistMotor);
            }
        }
    }

    @Override
    public void applyLiveState(TwoBodyConstraint constraint, FriendlyByteBuf buf) {
        if (constraint instanceof SwingTwistConstraint st) {
            st.setSwingMotorState(buf.readEnum(EMotorState.class));
            st.setTwistMotorState(buf.readEnum(EMotorState.class));

            try (MotorSettings swingMotor = st.getSwingMotorSettings()) {
                VxBufferUtil.loadMotorSettings(buf, swingMotor);
            }
            try (MotorSettings twistMotor = st.getTwistMotorSettings()) {
                VxBufferUtil.loadMotorSettings(buf, twistMotor);
            }
        }
    }
}