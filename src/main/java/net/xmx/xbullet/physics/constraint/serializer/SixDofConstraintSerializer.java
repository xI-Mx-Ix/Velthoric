package net.xmx.xbullet.physics.constraint.serializer;

import com.github.stephengold.joltjni.MotorSettings;
import com.github.stephengold.joltjni.SixDofConstraint;
import com.github.stephengold.joltjni.SixDofConstraintSettings;
import com.github.stephengold.joltjni.SpringSettings;
import com.github.stephengold.joltjni.enumerate.EAxis;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.enumerate.EMotorState;
import com.github.stephengold.joltjni.enumerate.ESwingType;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.xbullet.physics.constraint.builder.SixDofConstraintBuilder;
import net.xmx.xbullet.physics.constraint.serializer.base.ConstraintSerializer;
import net.xmx.xbullet.physics.constraint.util.BufferUtil;

public class SixDofConstraintSerializer implements ConstraintSerializer<SixDofConstraintBuilder, SixDofConstraint, SixDofConstraintSettings> {

    @Override public String getTypeId() { return "xbullet:six_dof"; }

    @Override
    public void serialize(SixDofConstraintBuilder builder, FriendlyByteBuf buf) {
        serializeBodies(builder, buf);
        buf.writeEnum(builder.space);
        BufferUtil.putRVec3(buf, builder.position1);
        BufferUtil.putRVec3(buf, builder.position2);
        BufferUtil.putVec3(buf, builder.axisX1);
        BufferUtil.putVec3(buf, builder.axisY1);
        BufferUtil.putVec3(buf, builder.axisX2);
        BufferUtil.putVec3(buf, builder.axisY2);
        buf.writeEnum(builder.swingType);

        for (EAxis axis : EAxis.values()) {
            buf.writeEnum(builder.axisStates.get(axis));
            buf.writeFloat(builder.limitsMin.get(axis));
            buf.writeFloat(builder.limitsMax.get(axis));
            buf.writeFloat(builder.maxFriction.get(axis));
            BufferUtil.putMotorSettings(buf, builder.motorSettings.get(axis));
            BufferUtil.putSpringSettings(buf, builder.limitsSpringSettings.get(axis));
        }

        for (EAxis axis : EAxis.values()) {
            buf.writeEnum(EMotorState.Off);
        }
        BufferUtil.putVec3(buf, null);
        BufferUtil.putQuat(buf, null);
        BufferUtil.putVec3(buf, null);
        BufferUtil.putVec3(buf, null);
    }

    @Override
    public SixDofConstraintSettings createSettings(FriendlyByteBuf buf) {
        SixDofConstraintSettings s = new SixDofConstraintSettings();
        s.setSpace(buf.readEnum(EConstraintSpace.class));
        s.setPosition1(BufferUtil.getRVec3(buf));
        s.setPosition2(BufferUtil.getRVec3(buf));
        s.setAxisX1(BufferUtil.getVec3(buf));
        s.setAxisY1(BufferUtil.getVec3(buf));
        s.setAxisX2(BufferUtil.getVec3(buf));
        s.setAxisY2(BufferUtil.getVec3(buf));
        s.setSwingType(buf.readEnum(ESwingType.class));

        for (EAxis axis : EAxis.values()) {
            SixDofConstraintBuilder.AxisState state = buf.readEnum(SixDofConstraintBuilder.AxisState.class);
            float min = buf.readFloat();
            float max = buf.readFloat();
            float friction = buf.readFloat();
            
            switch (state) {
                case FREE -> s.makeFreeAxis(axis);
                case LIMITED -> s.setLimitedAxis(axis, min, max);
                case FIXED -> s.makeFixedAxis(axis);
            }
            s.setMaxFriction(axis, friction);
            
            try (MotorSettings ms = s.getMotorSettings(axis)) {
                BufferUtil.loadMotorSettings(buf, ms);
            }
            try(SpringSettings ss = s.getLimitsSpringSettings(axis)) {
                 BufferUtil.loadSpringSettings(buf, ss);
            }
        }
        return s;
    }

    @Override
    public void applyLiveState(SixDofConstraint constraint, FriendlyByteBuf buf) {
        for (EAxis axis : EAxis.values()) {
            constraint.setMotorState(axis, buf.readEnum(EMotorState.class));
        }
        constraint.setTargetPositionCs(BufferUtil.getVec3(buf));
        constraint.setTargetOrientationCs(BufferUtil.getQuat(buf));
        constraint.setTargetVelocityCs(BufferUtil.getVec3(buf));
        constraint.setTargetAngularVelocityCs(BufferUtil.getVec3(buf));
    }
}