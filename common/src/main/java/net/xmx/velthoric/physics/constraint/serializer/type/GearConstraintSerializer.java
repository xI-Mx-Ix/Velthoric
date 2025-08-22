package net.xmx.velthoric.physics.constraint.serializer.type;

import com.github.stephengold.joltjni.GearConstraintSettings;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import io.netty.buffer.ByteBuf;
import net.xmx.velthoric.physics.constraint.serializer.ConstraintSerializerUtils;
import net.xmx.velthoric.physics.constraint.serializer.IVxConstraintSerializer;
import net.xmx.velthoric.physics.constraint.serializer.VxConstraintSerializer;

public class GearConstraintSerializer extends VxConstraintSerializer implements IVxConstraintSerializer<GearConstraintSettings> {

    @Override
    public void save(GearConstraintSettings settings, ByteBuf buf) {
        saveBase(settings, buf);
        buf.writeInt(settings.getSpace().ordinal());
        ConstraintSerializerUtils.saveVec3(settings.getHingeAxis1(), buf);
        ConstraintSerializerUtils.saveVec3(settings.getHingeAxis2(), buf);
        buf.writeFloat(settings.getRatio());
    }

    @Override
    public GearConstraintSettings load(ByteBuf buf) {
        GearConstraintSettings settings = new GearConstraintSettings();
        loadBase(settings, buf);
        settings.setSpace(EConstraintSpace.values()[buf.readInt()]);
        settings.setHingeAxis1(ConstraintSerializerUtils.loadVec3(buf));
        settings.setHingeAxis2(ConstraintSerializerUtils.loadVec3(buf));
        settings.setRatio(buf.readFloat());
        return settings;
    }
}