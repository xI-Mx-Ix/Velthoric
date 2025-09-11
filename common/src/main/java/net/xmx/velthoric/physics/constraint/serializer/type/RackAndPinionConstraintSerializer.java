/*
This file is part of Velthoric.
Licensed under LGPL 3.0.
*/
package net.xmx.velthoric.physics.constraint.serializer.type;

import com.github.stephengold.joltjni.RackAndPinionConstraintSettings;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import io.netty.buffer.ByteBuf;
import net.xmx.velthoric.physics.constraint.serializer.ConstraintSerializerUtils;
import net.xmx.velthoric.physics.constraint.serializer.IVxConstraintSerializer;
import net.xmx.velthoric.physics.constraint.serializer.VxConstraintSerializer;

public class RackAndPinionConstraintSerializer extends VxConstraintSerializer implements IVxConstraintSerializer<RackAndPinionConstraintSettings> {

    @Override
    public void save(RackAndPinionConstraintSettings settings, ByteBuf buf) {
        saveBase(settings, buf);
        buf.writeInt(settings.getSpace().ordinal());
        ConstraintSerializerUtils.saveVec3(settings.getHingeAxis(), buf);
        ConstraintSerializerUtils.saveVec3(settings.getSliderAxis(), buf);
        buf.writeFloat(settings.getRatio());
    }

    @Override
    public RackAndPinionConstraintSettings load(ByteBuf buf) {
        RackAndPinionConstraintSettings settings = new RackAndPinionConstraintSettings();
        loadBase(settings, buf);
        settings.setSpace(EConstraintSpace.values()[buf.readInt()]);
        settings.setHingeAxis(ConstraintSerializerUtils.loadVec3(buf));
        settings.setSliderAxis(ConstraintSerializerUtils.loadVec3(buf));
        settings.setRatio(buf.readFloat());
        return settings;
    }
}