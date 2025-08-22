package net.xmx.vortex.physics.constraint.serializer.type;

import com.github.stephengold.joltjni.HingeConstraintSettings;
import com.github.stephengold.joltjni.MotorSettings;
import com.github.stephengold.joltjni.SpringSettings;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import io.netty.buffer.ByteBuf;
import net.xmx.vortex.physics.constraint.serializer.ConstraintSerializerUtils;
import net.xmx.vortex.physics.constraint.serializer.IVxConstraintSerializer;
import net.xmx.vortex.physics.constraint.serializer.VxConstraintSerializer;

public class HingeConstraintSerializer extends VxConstraintSerializer implements IVxConstraintSerializer<HingeConstraintSettings> {

    @Override
    public void save(HingeConstraintSettings settings, ByteBuf buf) {
        saveBase(settings, buf);
        buf.writeInt(settings.getSpace().ordinal());
        ConstraintSerializerUtils.saveRVec3(settings.getPoint1(), buf);
        ConstraintSerializerUtils.saveRVec3(settings.getPoint2(), buf);
        ConstraintSerializerUtils.saveVec3(settings.getHingeAxis1(), buf);
        ConstraintSerializerUtils.saveVec3(settings.getNormalAxis1(), buf);
        ConstraintSerializerUtils.saveVec3(settings.getHingeAxis2(), buf);
        ConstraintSerializerUtils.saveVec3(settings.getNormalAxis2(), buf);
        buf.writeFloat(settings.getLimitsMin());
        buf.writeFloat(settings.getLimitsMax());
        buf.writeFloat(settings.getMaxFrictionTorque());
        try (SpringSettings ls = settings.getLimitsSpringSettings()) {
            ConstraintSerializerUtils.saveSpring(ls, buf);
        }
        try (MotorSettings ms = settings.getMotorSettings()) {
            ConstraintSerializerUtils.saveMotor(ms, buf);
        }
    }

    @Override
    public HingeConstraintSettings load(ByteBuf buf) {
        HingeConstraintSettings settings = new HingeConstraintSettings();
        loadBase(settings, buf);
        settings.setSpace(EConstraintSpace.values()[buf.readInt()]);
        settings.setPoint1(ConstraintSerializerUtils.loadRVec3(buf));
        settings.setPoint2(ConstraintSerializerUtils.loadRVec3(buf));
        settings.setHingeAxis1(ConstraintSerializerUtils.loadVec3(buf));
        settings.setNormalAxis1(ConstraintSerializerUtils.loadVec3(buf));
        settings.setHingeAxis2(ConstraintSerializerUtils.loadVec3(buf));
        settings.setNormalAxis2(ConstraintSerializerUtils.loadVec3(buf));
        settings.setLimitsMin(buf.readFloat());
        settings.setLimitsMax(buf.readFloat());
        settings.setMaxFrictionTorque(buf.readFloat());
        try (SpringSettings ls = settings.getLimitsSpringSettings()) {
            ConstraintSerializerUtils.loadSpring(ls, buf);
        }
        try (MotorSettings ms = settings.getMotorSettings()) {
            ConstraintSerializerUtils.loadMotor(ms, buf);
        }
        return settings;
    }
}