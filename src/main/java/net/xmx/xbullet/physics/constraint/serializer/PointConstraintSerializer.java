package net.xmx.xbullet.physics.constraint.serializer;

import com.github.stephengold.joltjni.PointConstraint;
import com.github.stephengold.joltjni.PointConstraintSettings;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.xbullet.physics.constraint.builder.PointConstraintBuilder;
import net.xmx.xbullet.physics.constraint.serializer.base.ConstraintSerializer;
import net.xmx.xbullet.physics.constraint.util.BufferUtil;

public class PointConstraintSerializer implements ConstraintSerializer<PointConstraintBuilder, PointConstraint, PointConstraintSettings> {

    @Override
    public String getTypeId() {
        return "xbullet:point";
    }

    @Override
    public void serializeSettings(PointConstraintBuilder builder, FriendlyByteBuf buf) {
        PointConstraintSettings settings = builder.getSettings();
        buf.writeEnum(settings.getSpace());
        BufferUtil.putRVec3(buf, settings.getPoint1());
        BufferUtil.putRVec3(buf, settings.getPoint2());
    }

    @Override
    public PointConstraintSettings createSettings(FriendlyByteBuf buf) {
        PointConstraintSettings s = new PointConstraintSettings();
        s.setSpace(buf.readEnum(EConstraintSpace.class));
        s.setPoint1(BufferUtil.getRVec3(buf));
        s.setPoint2(BufferUtil.getRVec3(buf));
        return s;
    }
}