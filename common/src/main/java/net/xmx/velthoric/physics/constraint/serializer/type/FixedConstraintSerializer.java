/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.constraint.serializer.type;

import com.github.stephengold.joltjni.FixedConstraintSettings;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import io.netty.buffer.ByteBuf;
import net.xmx.velthoric.physics.constraint.serializer.ConstraintSerializerUtils;
import net.xmx.velthoric.physics.constraint.serializer.IVxConstraintSerializer;
import net.xmx.velthoric.physics.constraint.serializer.VxConstraintSerializer;

public class FixedConstraintSerializer extends VxConstraintSerializer implements IVxConstraintSerializer<FixedConstraintSettings> {

    @Override
    public void save(FixedConstraintSettings settings, ByteBuf buf) {
        saveBase(settings, buf);
        buf.writeInt(settings.getSpace().ordinal());
        buf.writeBoolean(settings.getAutoDetectPoint());
        ConstraintSerializerUtils.saveRVec3(settings.getPoint1(), buf);
        ConstraintSerializerUtils.saveRVec3(settings.getPoint2(), buf);
        ConstraintSerializerUtils.saveVec3(settings.getAxisX1(), buf);
        ConstraintSerializerUtils.saveVec3(settings.getAxisY1(), buf);
        ConstraintSerializerUtils.saveVec3(settings.getAxisX2(), buf);
        ConstraintSerializerUtils.saveVec3(settings.getAxisY2(), buf);
    }

    @Override
    public FixedConstraintSettings load(ByteBuf buf) {
        FixedConstraintSettings settings = new FixedConstraintSettings();
        loadBase(settings, buf);
        settings.setSpace(EConstraintSpace.values()[buf.readInt()]);
        settings.setAutoDetectPoint(buf.readBoolean());
        settings.setPoint1(ConstraintSerializerUtils.loadRVec3(buf));
        settings.setPoint2(ConstraintSerializerUtils.loadRVec3(buf));
        settings.setAxisX1(ConstraintSerializerUtils.loadVec3(buf));
        settings.setAxisY1(ConstraintSerializerUtils.loadVec3(buf));
        settings.setAxisX2(ConstraintSerializerUtils.loadVec3(buf));
        settings.setAxisY2(ConstraintSerializerUtils.loadVec3(buf));
        return settings;
    }
}
