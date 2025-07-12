package net.xmx.xbullet.physics.constraint.serializer;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.SwingTwistConstraint;
import com.github.stephengold.joltjni.SwingTwistConstraintSettings;
import com.github.stephengold.joltjni.Vec3;
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
    public void serialize(SwingTwistConstraintBuilder builder, FriendlyByteBuf buf) {
        serializeBodies(builder, buf);
        buf.writeEnum(builder.space);
        buf.writeEnum(builder.swingType);
        BufferUtil.putRVec3(buf, builder.position1);
        BufferUtil.putRVec3(buf, builder.position2);
        BufferUtil.putVec3(buf, builder.twistAxis1);
        BufferUtil.putVec3(buf, builder.twistAxis2);
        BufferUtil.putVec3(buf, builder.planeAxis1);
        BufferUtil.putVec3(buf, builder.planeAxis2);
        buf.writeFloat(builder.normalHalfConeAngle);
        buf.writeFloat(builder.planeHalfConeAngle);
        buf.writeFloat(builder.twistMinAngle);
        buf.writeFloat(builder.twistMaxAngle);
        buf.writeFloat(builder.maxFrictionTorque);
        BufferUtil.putMotorSettings(buf, builder.swingMotorSettings);
        BufferUtil.putMotorSettings(buf, builder.twistMotorSettings);

        // Live state placeholders
        buf.writeEnum(EMotorState.Off); // Swing Motor
        buf.writeEnum(EMotorState.Off); // Twist Motor
        BufferUtil.putQuat(buf, new Quat()); // Target Orientation
        BufferUtil.putVec3(buf, new Vec3()); // Target Angular Velocity
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
        BufferUtil.loadMotorSettings(buf, s.getSwingMotorSettings());
        BufferUtil.loadMotorSettings(buf, s.getTwistMotorSettings());
        return s;
    }

    @Override
    public void applyLiveState(SwingTwistConstraint constraint, FriendlyByteBuf buf) {
        constraint.setSwingMotorState(buf.readEnum(EMotorState.class));
        constraint.setTwistMotorState(buf.readEnum(EMotorState.class));
        constraint.setTargetOrientationCs(BufferUtil.getQuat(buf));
        constraint.setTargetAngularVelocityCs(BufferUtil.getVec3(buf));
    }
}