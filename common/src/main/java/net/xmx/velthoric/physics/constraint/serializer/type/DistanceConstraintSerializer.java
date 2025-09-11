/*
This file is part of Velthoric.
Licensed under LGPL 3.0.
*/
package net.xmx.velthoric.physics.constraint.serializer.type;

import com.github.stephengold.joltjni.DistanceConstraintSettings;
import com.github.stephengold.joltjni.SpringSettings;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import io.netty.buffer.ByteBuf;
import net.xmx.velthoric.physics.constraint.serializer.ConstraintSerializerUtils;
import net.xmx.velthoric.physics.constraint.serializer.IVxConstraintSerializer;
import net.xmx.velthoric.physics.constraint.serializer.VxConstraintSerializer;

public class DistanceConstraintSerializer extends VxConstraintSerializer implements IVxConstraintSerializer<DistanceConstraintSettings> {

    @Override
    public void save(DistanceConstraintSettings settings, ByteBuf buf) {
        saveBase(settings, buf);
        buf.writeInt(settings.getSpace().ordinal());
        ConstraintSerializerUtils.saveRVec3(settings.getPoint1(), buf);
        ConstraintSerializerUtils.saveRVec3(settings.getPoint2(), buf);
        buf.writeFloat(settings.getMinDistance());
        buf.writeFloat(settings.getMaxDistance());
        try (SpringSettings ss = settings.getLimitsSpringSettings()) {
            ConstraintSerializerUtils.saveSpring(ss, buf);
        }
    }

    @Override
    public DistanceConstraintSettings load(ByteBuf buf) {
        DistanceConstraintSettings settings = new DistanceConstraintSettings();
        loadBase(settings, buf);
        settings.setSpace(EConstraintSpace.values()[buf.readInt()]);
        settings.setPoint1(ConstraintSerializerUtils.loadRVec3(buf));
        settings.setPoint2(ConstraintSerializerUtils.loadRVec3(buf));
        settings.setMinDistance(buf.readFloat());
        settings.setMaxDistance(buf.readFloat());
        try (SpringSettings ss = settings.getLimitsSpringSettings()) {
            ConstraintSerializerUtils.loadSpring(ss, buf);
        }
        return settings;
    }
}