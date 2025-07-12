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
    public void serialize(PulleyConstraintBuilder builder, FriendlyByteBuf buf) {
        serializeBodies(builder, buf);
        buf.writeEnum(builder.space);
        BufferUtil.putRVec3(buf, builder.bodyPoint1);
        BufferUtil.putRVec3(buf, builder.bodyPoint2);
        BufferUtil.putRVec3(buf, builder.fixedPoint1);
        BufferUtil.putRVec3(buf, builder.fixedPoint2);
        buf.writeFloat(builder.ratio);
        buf.writeFloat(builder.minLength);
        buf.writeFloat(builder.maxLength);
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

    @Override
    public void applyLiveState(PulleyConstraint constraint, FriendlyByteBuf buf) {
        // PulleyConstraint has no live state to apply after creation based on JoltJNI.
    }
}