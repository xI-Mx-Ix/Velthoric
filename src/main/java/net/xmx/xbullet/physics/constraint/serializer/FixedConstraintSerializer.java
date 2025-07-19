package net.xmx.xbullet.physics.constraint.serializer;

import com.github.stephengold.joltjni.FixedConstraint;
import com.github.stephengold.joltjni.FixedConstraintSettings;
import com.github.stephengold.joltjni.TwoBodyConstraint;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.xbullet.physics.constraint.builder.FixedConstraintBuilder;
import net.xmx.xbullet.physics.constraint.serializer.base.ConstraintSerializer;
import net.xmx.xbullet.physics.constraint.util.BufferUtil;

public class FixedConstraintSerializer implements ConstraintSerializer<FixedConstraintBuilder, FixedConstraint, FixedConstraintSettings> {

    @Override
    public String getTypeId() {
        return "xbullet:fixed";
    }

    @Override
    public void serializeSettings(FixedConstraintBuilder builder, FriendlyByteBuf buf) {
        FixedConstraintSettings settings = builder.getSettings();
        buf.writeEnum(settings.getSpace());
        buf.writeBoolean(settings.getAutoDetectPoint());
        BufferUtil.putRVec3(buf, settings.getPoint1());
        BufferUtil.putRVec3(buf, settings.getPoint2());
        BufferUtil.putVec3(buf, settings.getAxisX1());
        BufferUtil.putVec3(buf, settings.getAxisY1());
        BufferUtil.putVec3(buf, settings.getAxisX2());
        BufferUtil.putVec3(buf, settings.getAxisY2());
    }

    @Override
    public FixedConstraintSettings createSettings(FriendlyByteBuf buf) {
        FixedConstraintSettings s = new FixedConstraintSettings();
        s.setSpace(buf.readEnum(EConstraintSpace.class));
        s.setAutoDetectPoint(buf.readBoolean());
        s.setPoint1(BufferUtil.getRVec3(buf));
        s.setPoint2(BufferUtil.getRVec3(buf));
        s.setAxisX1(BufferUtil.getVec3(buf));
        s.setAxisY1(BufferUtil.getVec3(buf));
        s.setAxisX2(BufferUtil.getVec3(buf));
        s.setAxisY2(BufferUtil.getVec3(buf));
        return s;
    }
}