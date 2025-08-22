package net.xmx.vortex.physics.constraint.serializer.type;

import com.github.stephengold.joltjni.MotorSettings;
import com.github.stephengold.joltjni.SliderConstraintSettings;
import com.github.stephengold.joltjni.SpringSettings;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import io.netty.buffer.ByteBuf;
import net.xmx.vortex.physics.constraint.serializer.ConstraintSerializerUtils;
import net.xmx.vortex.physics.constraint.serializer.IVxConstraintSerializer;
import net.xmx.vortex.physics.constraint.serializer.VxConstraintSerializer;

public class SliderConstraintSerializer extends VxConstraintSerializer implements IVxConstraintSerializer<SliderConstraintSettings> {

    @Override
    public void save(SliderConstraintSettings settings, ByteBuf buf) {
        saveBase(settings, buf);
        buf.writeInt(settings.getSpace().ordinal());
        buf.writeBoolean(settings.getAutoDetectPoint());
        ConstraintSerializerUtils.saveRVec3(settings.getPoint1(), buf);
        ConstraintSerializerUtils.saveRVec3(settings.getPoint2(), buf);
        ConstraintSerializerUtils.saveVec3(settings.getSliderAxis1(), buf);
        ConstraintSerializerUtils.saveVec3(settings.getNormalAxis1(), buf);
        ConstraintSerializerUtils.saveVec3(settings.getSliderAxis2(), buf);
        ConstraintSerializerUtils.saveVec3(settings.getNormalAxis2(), buf);
        buf.writeFloat(settings.getLimitsMin());
        buf.writeFloat(settings.getLimitsMax());
        buf.writeFloat(settings.getMaxFrictionForce());
        try (SpringSettings ss = settings.getLimitsSpringSettings()) {
            ConstraintSerializerUtils.saveSpring(ss, buf);
        }
        try (MotorSettings ms = settings.getMotorSettings()) {
            ConstraintSerializerUtils.saveMotor(ms, buf);
        }
    }

    @Override
    public SliderConstraintSettings load(ByteBuf buf) {
        SliderConstraintSettings settings = new SliderConstraintSettings();
        loadBase(settings, buf);
        settings.setSpace(EConstraintSpace.values()[buf.readInt()]);
        settings.setAutoDetectPoint(buf.readBoolean());
        settings.setPoint1(ConstraintSerializerUtils.loadRVec3(buf));
        settings.setPoint2(ConstraintSerializerUtils.loadRVec3(buf));
        settings.setSliderAxis1(ConstraintSerializerUtils.loadVec3(buf));
        settings.setNormalAxis1(ConstraintSerializerUtils.loadVec3(buf));
        settings.setSliderAxis2(ConstraintSerializerUtils.loadVec3(buf));
        settings.setNormalAxis2(ConstraintSerializerUtils.loadVec3(buf));
        settings.setLimitsMin(buf.readFloat());
        settings.setLimitsMax(buf.readFloat());
        settings.setMaxFrictionForce(buf.readFloat());
        try (SpringSettings ss = settings.getLimitsSpringSettings()) {
            ConstraintSerializerUtils.loadSpring(ss, buf);
        }
        try (MotorSettings ms = settings.getMotorSettings()) {
            ConstraintSerializerUtils.loadMotor(ms, buf);
        }
        return settings;
    }
}