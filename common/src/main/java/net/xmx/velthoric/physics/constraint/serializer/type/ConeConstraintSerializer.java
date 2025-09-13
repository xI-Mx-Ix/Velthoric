/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.constraint.serializer.type;

import com.github.stephengold.joltjni.ConeConstraintSettings;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import io.netty.buffer.ByteBuf;
import net.xmx.velthoric.physics.constraint.serializer.ConstraintSerializerUtils;
import net.xmx.velthoric.physics.constraint.serializer.IVxConstraintSerializer;
import net.xmx.velthoric.physics.constraint.serializer.VxConstraintSerializer;

public class ConeConstraintSerializer extends VxConstraintSerializer implements IVxConstraintSerializer<ConeConstraintSettings> {

    @Override
    public void save(ConeConstraintSettings settings, ByteBuf buf) {
        saveBase(settings, buf);
        buf.writeInt(settings.getSpace().ordinal());
        ConstraintSerializerUtils.saveRVec3(settings.getPoint1(), buf);
        ConstraintSerializerUtils.saveRVec3(settings.getPoint2(), buf);
        ConstraintSerializerUtils.saveVec3(settings.getTwistAxis1(), buf);
        ConstraintSerializerUtils.saveVec3(settings.getTwistAxis2(), buf);
        buf.writeFloat(settings.getHalfConeAngle());
    }

    @Override
    public ConeConstraintSettings load(ByteBuf buf) {
        ConeConstraintSettings settings = new ConeConstraintSettings();
        loadBase(settings, buf);
        settings.setSpace(EConstraintSpace.values()[buf.readInt()]);
        settings.setPoint1(ConstraintSerializerUtils.loadRVec3(buf));
        settings.setPoint2(ConstraintSerializerUtils.loadRVec3(buf));
        settings.setTwistAxis1(ConstraintSerializerUtils.loadVec3(buf));
        settings.setTwistAxis2(ConstraintSerializerUtils.loadVec3(buf));
        settings.setHalfConeAngle(buf.readFloat());
        return settings;
    }
}
