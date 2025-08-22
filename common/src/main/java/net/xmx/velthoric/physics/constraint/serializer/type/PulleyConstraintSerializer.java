package net.xmx.velthoric.physics.constraint.serializer.type;

import com.github.stephengold.joltjni.PulleyConstraintSettings;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import io.netty.buffer.ByteBuf;
import net.xmx.velthoric.physics.constraint.serializer.ConstraintSerializerUtils;
import net.xmx.velthoric.physics.constraint.serializer.IVxConstraintSerializer;
import net.xmx.velthoric.physics.constraint.serializer.VxConstraintSerializer;

public class PulleyConstraintSerializer extends VxConstraintSerializer implements IVxConstraintSerializer<PulleyConstraintSettings> {

    @Override
    public void save(PulleyConstraintSettings settings, ByteBuf buf) {
        saveBase(settings, buf);
        buf.writeInt(settings.getSpace().ordinal());
        ConstraintSerializerUtils.saveRVec3(settings.getFixedPoint1(), buf);
        ConstraintSerializerUtils.saveRVec3(settings.getFixedPoint2(), buf);
        ConstraintSerializerUtils.saveRVec3(settings.getBodyPoint1(), buf);
        ConstraintSerializerUtils.saveRVec3(settings.getBodyPoint2(), buf);
        buf.writeFloat(settings.getRatio());
        buf.writeFloat(settings.getMinLength());
        buf.writeFloat(settings.getMaxLength());
    }

    @Override
    public PulleyConstraintSettings load(ByteBuf buf) {
        PulleyConstraintSettings settings = new PulleyConstraintSettings();
        loadBase(settings, buf);
        settings.setSpace(EConstraintSpace.values()[buf.readInt()]);
        settings.setFixedPoint1(ConstraintSerializerUtils.loadRVec3(buf));
        settings.setFixedPoint2(ConstraintSerializerUtils.loadRVec3(buf));
        settings.setBodyPoint1(ConstraintSerializerUtils.loadRVec3(buf));
        settings.setBodyPoint2(ConstraintSerializerUtils.loadRVec3(buf));
        settings.setRatio(buf.readFloat());
        settings.setMinLength(buf.readFloat());
        settings.setMaxLength(buf.readFloat());
        return settings;
    }
}