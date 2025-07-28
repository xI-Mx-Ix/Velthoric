package net.xmx.vortex.physics.constraint.serializer;

import com.github.stephengold.joltjni.PointConstraint;
import com.github.stephengold.joltjni.PointConstraintSettings;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.vortex.physics.constraint.builder.PointConstraintBuilder;
import net.xmx.vortex.physics.constraint.serializer.base.ConstraintSerializer;
import net.xmx.vortex.physics.constraint.util.VxBufferUtil;

public class PointConstraintSerializer implements ConstraintSerializer<PointConstraintBuilder, PointConstraint, PointConstraintSettings> {

    @Override
    public String getTypeId() {
        return "vortex:point";
    }

    @Override
    public void serializeSettings(PointConstraintBuilder builder, FriendlyByteBuf buf) {
        PointConstraintSettings settings = builder.getSettings();
        buf.writeEnum(settings.getSpace());
        VxBufferUtil.putRVec3(buf, settings.getPoint1());
        VxBufferUtil.putRVec3(buf, settings.getPoint2());
    }

    @Override
    public PointConstraintSettings createSettings(FriendlyByteBuf buf) {
        PointConstraintSettings s = new PointConstraintSettings();
        s.setSpace(buf.readEnum(EConstraintSpace.class));
        s.setPoint1(VxBufferUtil.getRVec3(buf));
        s.setPoint2(VxBufferUtil.getRVec3(buf));
        return s;
    }
}