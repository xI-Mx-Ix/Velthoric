package net.xmx.vortex.physics.constraint.serializer.type;

import com.github.stephengold.joltjni.MotorSettings;
import com.github.stephengold.joltjni.SwingTwistConstraintSettings;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.enumerate.ESwingType;
import io.netty.buffer.ByteBuf;
import net.xmx.vortex.physics.constraint.serializer.ConstraintSerializerUtils;
import net.xmx.vortex.physics.constraint.serializer.IVxConstraintSerializer;
import net.xmx.vortex.physics.constraint.serializer.VxConstraintSerializer;

public class SwingTwistConstraintSerializer extends VxConstraintSerializer implements IVxConstraintSerializer<SwingTwistConstraintSettings> {

    @Override
    public void save(SwingTwistConstraintSettings settings, ByteBuf buf) {
        saveBase(settings, buf);
        buf.writeInt(settings.getSpace().ordinal());
        buf.writeInt(settings.getSwingType().ordinal());

        ConstraintSerializerUtils.saveRVec3(settings.getPosition1(), buf);
        ConstraintSerializerUtils.saveRVec3(settings.getPosition2(), buf);
        ConstraintSerializerUtils.saveVec3(settings.getTwistAxis1(), buf);
        ConstraintSerializerUtils.saveVec3(settings.getPlaneAxis1(), buf);
        ConstraintSerializerUtils.saveVec3(settings.getTwistAxis2(), buf);
        ConstraintSerializerUtils.saveVec3(settings.getPlaneAxis2(), buf);

        buf.writeFloat(settings.getNormalHalfConeAngle());
        buf.writeFloat(settings.getPlaneHalfConeAngle());
        buf.writeFloat(settings.getTwistMinAngle());
        buf.writeFloat(settings.getTwistMaxAngle());
        buf.writeFloat(settings.getMaxFrictionTorque());

        try (MotorSettings twistMotor = settings.getTwistMotorSettings()) {
            ConstraintSerializerUtils.saveMotor(twistMotor, buf);
        }
        try (MotorSettings swingMotor = settings.getSwingMotorSettings()) {
            ConstraintSerializerUtils.saveMotor(swingMotor, buf);
        }
    }

    @Override
    public SwingTwistConstraintSettings load(ByteBuf buf) {
        SwingTwistConstraintSettings settings = new SwingTwistConstraintSettings();
        loadBase(settings, buf);
        settings.setSpace(EConstraintSpace.values()[buf.readInt()]);
        settings.setSwingType(ESwingType.values()[buf.readInt()]);

        settings.setPosition1(ConstraintSerializerUtils.loadRVec3(buf));
        settings.setPosition2(ConstraintSerializerUtils.loadRVec3(buf));
        settings.setTwistAxis1(ConstraintSerializerUtils.loadVec3(buf));
        settings.setPlaneAxis1(ConstraintSerializerUtils.loadVec3(buf));
        settings.setTwistAxis2(ConstraintSerializerUtils.loadVec3(buf));
        settings.setPlaneAxis2(ConstraintSerializerUtils.loadVec3(buf));

        settings.setNormalHalfConeAngle(buf.readFloat());
        settings.setPlaneHalfConeAngle(buf.readFloat());
        settings.setTwistMinAngle(buf.readFloat());
        settings.setTwistMaxAngle(buf.readFloat());
        settings.setMaxFrictionTorque(buf.readFloat());

        try (MotorSettings twistMotor = settings.getTwistMotorSettings()) {
            ConstraintSerializerUtils.loadMotor(twistMotor, buf);
        }
        try (MotorSettings swingMotor = settings.getSwingMotorSettings()) {
            ConstraintSerializerUtils.loadMotor(swingMotor, buf);
        }
        return settings;
    }
}