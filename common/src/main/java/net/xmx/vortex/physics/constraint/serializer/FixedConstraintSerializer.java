package net.xmx.vortex.physics.constraint.serializer;

import com.github.stephengold.joltjni.FixedConstraint;
import com.github.stephengold.joltjni.FixedConstraintSettings;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.vortex.physics.constraint.builder.FixedConstraintBuilder;
import net.xmx.vortex.physics.constraint.serializer.base.ConstraintSerializer;
import net.xmx.vortex.physics.constraint.util.VxBufferUtil;

public class FixedConstraintSerializer implements ConstraintSerializer<FixedConstraintBuilder, FixedConstraint, FixedConstraintSettings> {

    @Override
    public String getTypeId() {
        return "vortex:fixed";
    }

    @Override
    public void serializeSettings(FixedConstraintBuilder builder, FriendlyByteBuf buf) {
        FixedConstraintSettings settings = builder.getSettings();
        buf.writeEnum(settings.getSpace());
        buf.writeBoolean(settings.getAutoDetectPoint());
        VxBufferUtil.putRVec3(buf, settings.getPoint1());
        VxBufferUtil.putRVec3(buf, settings.getPoint2());
        VxBufferUtil.putVec3(buf, settings.getAxisX1());
        VxBufferUtil.putVec3(buf, settings.getAxisY1());
        VxBufferUtil.putVec3(buf, settings.getAxisX2());
        VxBufferUtil.putVec3(buf, settings.getAxisY2());
    }

    @Override
    public FixedConstraintSettings createSettings(FriendlyByteBuf buf) {
        FixedConstraintSettings s = new FixedConstraintSettings();
        s.setSpace(buf.readEnum(EConstraintSpace.class));
        s.setAutoDetectPoint(buf.readBoolean());
        s.setPoint1(VxBufferUtil.getRVec3(buf));
        s.setPoint2(VxBufferUtil.getRVec3(buf));
        s.setAxisX1(VxBufferUtil.getVec3(buf));
        s.setAxisY1(VxBufferUtil.getVec3(buf));
        s.setAxisX2(VxBufferUtil.getVec3(buf));
        s.setAxisY2(VxBufferUtil.getVec3(buf));
        return s;
    }
}