package net.xmx.xbullet.physics.constraint.serializer;

import com.github.stephengold.joltjni.PulleyConstraint;
import com.github.stephengold.joltjni.PulleyConstraintSettings;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.xbullet.physics.constraint.builder.PulleyConstraintBuilder;
import net.xmx.xbullet.physics.constraint.serializer.base.ConstraintSerializer;
import net.xmx.xbullet.physics.constraint.util.BufferUtil;

public class PulleyConstraintSerializer implements ConstraintSerializer<PulleyConstraintBuilder, PulleyConstraint, PulleyConstraintSettings> {

    @Override
    public String getTypeId() {
        return "xbullet:pulley";
    }

    @Override
    public void serializeSettings(PulleyConstraintBuilder builder, FriendlyByteBuf buf) {
        PulleyConstraintSettings settings = builder.getSettings();
        buf.writeEnum(settings.getSpace());
        BufferUtil.putRVec3(buf, settings.getBodyPoint1());
        BufferUtil.putRVec3(buf, settings.getBodyPoint2());
        BufferUtil.putRVec3(buf, settings.getFixedPoint1());
        BufferUtil.putRVec3(buf, settings.getFixedPoint2());
        buf.writeFloat(settings.getRatio());
        buf.writeFloat(settings.getMinLength());
        buf.writeFloat(settings.getMaxLength());
    }

    @Override
    public PulleyConstraintSettings createSettings(FriendlyByteBuf buf) {
        PulleyConstraintSettings s = new PulleyConstraintSettings();
        s.setSpace(buf.readEnum(EConstraintSpace.class));
        s.setBodyPoint1(BufferUtil.getRVec3(buf));
        s.setBodyPoint2(BufferUtil.getRVec3(buf));
        s.setFixedPoint1(BufferUtil.getRVec3(buf));
        s.setFixedPoint2(BufferUtil.getRVec3(buf));
        s.setRatio(buf.readFloat());
        s.setMinLength(buf.readFloat());
        s.setMaxLength(buf.readFloat());
        return s;
    }
}