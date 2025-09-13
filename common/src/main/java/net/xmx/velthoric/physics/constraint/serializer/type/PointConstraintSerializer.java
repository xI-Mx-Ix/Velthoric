/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.constraint.serializer.type;

import com.github.stephengold.joltjni.PointConstraintSettings;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import io.netty.buffer.ByteBuf;
import net.xmx.velthoric.physics.constraint.serializer.ConstraintSerializerUtils;
import net.xmx.velthoric.physics.constraint.serializer.IVxConstraintSerializer;
import net.xmx.velthoric.physics.constraint.serializer.VxConstraintSerializer;

public class PointConstraintSerializer extends VxConstraintSerializer implements IVxConstraintSerializer<PointConstraintSettings> {

    @Override
    public void save(PointConstraintSettings settings, ByteBuf buf) {
        saveBase(settings, buf);
        buf.writeInt(settings.getSpace().ordinal());
        ConstraintSerializerUtils.saveRVec3(settings.getPoint1(), buf);
        ConstraintSerializerUtils.saveRVec3(settings.getPoint2(), buf);
    }

    @Override
    public PointConstraintSettings load(ByteBuf buf) {
        PointConstraintSettings settings = new PointConstraintSettings();
        loadBase(settings, buf);
        settings.setSpace(EConstraintSpace.values()[buf.readInt()]);
        settings.setPoint1(ConstraintSerializerUtils.loadRVec3(buf));
        settings.setPoint2(ConstraintSerializerUtils.loadRVec3(buf));
        return settings;
    }
}
