package net.xmx.vortex.physics.constraint.serializer;

import com.github.stephengold.joltjni.MotorSettings;
import com.github.stephengold.joltjni.SixDofConstraint;
import com.github.stephengold.joltjni.SixDofConstraintSettings;
import com.github.stephengold.joltjni.SpringSettings;
import com.github.stephengold.joltjni.TwoBodyConstraint;
import com.github.stephengold.joltjni.enumerate.EAxis;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.enumerate.EMotorState;
import com.github.stephengold.joltjni.enumerate.ESwingType;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.vortex.physics.constraint.builder.SixDofConstraintBuilder;
import net.xmx.vortex.physics.constraint.serializer.base.ConstraintSerializer;
import net.xmx.vortex.physics.constraint.util.VxBufferUtil;

public class SixDofConstraintSerializer implements ConstraintSerializer<SixDofConstraintBuilder, SixDofConstraint, SixDofConstraintSettings> {

    @Override
    public String getTypeId() {
        return "vortex:six_dof";
    }

    @Override
    public void serializeSettings(SixDofConstraintBuilder builder, FriendlyByteBuf buf) {
        SixDofConstraintSettings settings = builder.getSettings();
        buf.writeEnum(settings.getSpace());
        VxBufferUtil.putRVec3(buf, settings.getPosition1());
        VxBufferUtil.putRVec3(buf, settings.getPosition2());
        VxBufferUtil.putVec3(buf, settings.getAxisX1());
        VxBufferUtil.putVec3(buf, settings.getAxisY1());
        VxBufferUtil.putVec3(buf, settings.getAxisX2());
        VxBufferUtil.putVec3(buf, settings.getAxisY2());
        buf.writeEnum(settings.getSwingType());

        for (EAxis axis : EAxis.values()) {
            if (settings.isFixedAxis(axis)) {
                buf.writeEnum(SixDofConstraintBuilder.AxisState.FIXED);
            } else if (settings.isFreeAxis(axis)) {
                buf.writeEnum(SixDofConstraintBuilder.AxisState.FREE);
            } else {
                buf.writeEnum(SixDofConstraintBuilder.AxisState.LIMITED);
            }
            buf.writeFloat(settings.getLimitMin(axis));
            buf.writeFloat(settings.getLimitMax(axis));
            buf.writeFloat(settings.getMaxFriction(axis));
            try (MotorSettings ms = settings.getMotorSettings(axis)) {
                VxBufferUtil.putMotorSettings(buf, ms);
            }
            try (SpringSettings ss = settings.getLimitsSpringSettings(axis)) {
                VxBufferUtil.putSpringSettings(buf, ss);
            }
        }
    }

    @Override
    public SixDofConstraintSettings createSettings(FriendlyByteBuf buf) {
        SixDofConstraintSettings s = new SixDofConstraintSettings();
        s.setSpace(buf.readEnum(EConstraintSpace.class));
        s.setPosition1(VxBufferUtil.getRVec3(buf));
        s.setPosition2(VxBufferUtil.getRVec3(buf));
        s.setAxisX1(VxBufferUtil.getVec3(buf));
        s.setAxisY1(VxBufferUtil.getVec3(buf));
        s.setAxisX2(VxBufferUtil.getVec3(buf));
        s.setAxisY2(VxBufferUtil.getVec3(buf));
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
                VxBufferUtil.loadMotorSettings(buf, ms);
            }
            try (SpringSettings ss = s.getLimitsSpringSettings(axis)) {
                VxBufferUtil.loadSpringSettings(buf, ss);
            }
        }
        return s;
    }

    @Override
    public void serializeLiveState(TwoBodyConstraint constraint, FriendlyByteBuf buf) {
        if (constraint instanceof SixDofConstraint sixDof) {
            for (EAxis axis : EAxis.values()) {
                buf.writeEnum(sixDof.getMotorState(axis));
                try (MotorSettings motor = sixDof.getMotorSettings(axis)) {
                    VxBufferUtil.putMotorSettings(buf, motor);
                }
                try (SpringSettings spring = sixDof.getLimitsSpringSettings(axis)) {
                    VxBufferUtil.putSpringSettings(buf, spring);
                }
            }
        }
    }

    @Override
    public void applyLiveState(TwoBodyConstraint constraint, FriendlyByteBuf buf) {
        if (constraint instanceof SixDofConstraint sixDof) {
            for (EAxis axis : EAxis.values()) {
                sixDof.setMotorState(axis, buf.readEnum(EMotorState.class));
                try (MotorSettings motor = sixDof.getMotorSettings(axis)) {
                    VxBufferUtil.loadMotorSettings(buf, motor);
                }
                try (SpringSettings spring = sixDof.getLimitsSpringSettings(axis)) {
                    VxBufferUtil.loadSpringSettings(buf, spring);
                }
            }
        }
    }
}