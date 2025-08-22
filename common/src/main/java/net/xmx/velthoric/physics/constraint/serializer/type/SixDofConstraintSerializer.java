package net.xmx.velthoric.physics.constraint.serializer.type;

import com.github.stephengold.joltjni.MotorSettings;
import com.github.stephengold.joltjni.SixDofConstraintSettings;
import com.github.stephengold.joltjni.SpringSettings;
import com.github.stephengold.joltjni.enumerate.EAxis;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.enumerate.ESwingType;
import io.netty.buffer.ByteBuf;
import net.xmx.velthoric.physics.constraint.serializer.ConstraintSerializerUtils;
import net.xmx.velthoric.physics.constraint.serializer.IVxConstraintSerializer;
import net.xmx.velthoric.physics.constraint.serializer.VxConstraintSerializer;

public class SixDofConstraintSerializer extends VxConstraintSerializer implements IVxConstraintSerializer<SixDofConstraintSettings> {

    @Override
    public void save(SixDofConstraintSettings settings, ByteBuf buf) {
        saveBase(settings, buf);
        buf.writeInt(settings.getSpace().ordinal());
        buf.writeInt(settings.getSwingType().ordinal());

        ConstraintSerializerUtils.saveRVec3(settings.getPosition1(), buf);
        ConstraintSerializerUtils.saveRVec3(settings.getPosition2(), buf);
        ConstraintSerializerUtils.saveVec3(settings.getAxisX1(), buf);
        ConstraintSerializerUtils.saveVec3(settings.getAxisY1(), buf);
        ConstraintSerializerUtils.saveVec3(settings.getAxisX2(), buf);
        ConstraintSerializerUtils.saveVec3(settings.getAxisY2(), buf);

        for (EAxis axis : EAxis.values()) {
            buf.writeBoolean(settings.isFixedAxis(axis));
            buf.writeBoolean(settings.isFreeAxis(axis));
            if (!settings.isFixedAxis(axis) && !settings.isFreeAxis(axis)) {
                buf.writeFloat(settings.getLimitMin(axis));
                buf.writeFloat(settings.getLimitMax(axis));
            }
            try (SpringSettings ss = settings.getLimitsSpringSettings(axis)) {
                ConstraintSerializerUtils.saveSpring(ss, buf);
            }
            try (MotorSettings ms = settings.getMotorSettings(axis)) {
                ConstraintSerializerUtils.saveMotor(ms, buf);
            }
            buf.writeFloat(settings.getMaxFriction(axis));
        }
    }

    @Override
    public SixDofConstraintSettings load(ByteBuf buf) {
        SixDofConstraintSettings settings = new SixDofConstraintSettings();
        loadBase(settings, buf);
        settings.setSpace(EConstraintSpace.values()[buf.readInt()]);
        settings.setSwingType(ESwingType.values()[buf.readInt()]);

        settings.setPosition1(ConstraintSerializerUtils.loadRVec3(buf));
        settings.setPosition2(ConstraintSerializerUtils.loadRVec3(buf));
        settings.setAxisX1(ConstraintSerializerUtils.loadVec3(buf));
        settings.setAxisY1(ConstraintSerializerUtils.loadVec3(buf));
        settings.setAxisX2(ConstraintSerializerUtils.loadVec3(buf));
        settings.setAxisY2(ConstraintSerializerUtils.loadVec3(buf));

        for (EAxis axis : EAxis.values()) {
            boolean isFixed = buf.readBoolean();
            boolean isFree = buf.readBoolean();

            if (isFixed) {
                settings.makeFixedAxis(axis);
            } else if (isFree) {
                settings.makeFreeAxis(axis);
            } else {
                settings.setLimitedAxis(axis, buf.readFloat(), buf.readFloat());
            }

            try (SpringSettings ss = settings.getLimitsSpringSettings(axis)) {
                ConstraintSerializerUtils.loadSpring(ss, buf);
            }
            try (MotorSettings ms = settings.getMotorSettings(axis)) {
                ConstraintSerializerUtils.loadMotor(ms, buf);
            }
            settings.setMaxFriction(axis, buf.readFloat());
        }
        return settings;
    }
}