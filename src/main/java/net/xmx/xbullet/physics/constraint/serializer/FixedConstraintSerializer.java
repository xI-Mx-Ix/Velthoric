package net.xmx.xbullet.physics.constraint.serializer;

import com.github.stephengold.joltjni.FixedConstraint;
import com.github.stephengold.joltjni.FixedConstraintSettings;
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
    public void serialize(FixedConstraintBuilder builder, FriendlyByteBuf buf) {
        serializeBodies(builder, buf);
        buf.writeEnum(builder.space);
        buf.writeBoolean(builder.autoDetectPoint);
        BufferUtil.putRVec3(buf, builder.point1);
        BufferUtil.putRVec3(buf, builder.point2);
        BufferUtil.putVec3(buf, builder.axisX1);
        BufferUtil.putVec3(buf, builder.axisY1);
        BufferUtil.putVec3(buf, builder.axisX2);
        BufferUtil.putVec3(buf, builder.axisY2);
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

    @Override
    public void applyLiveState(FixedConstraint constraint, FriendlyByteBuf buf) {
        // FixedConstraint has no live state to apply after creation based on JoltJNI.
    }
}