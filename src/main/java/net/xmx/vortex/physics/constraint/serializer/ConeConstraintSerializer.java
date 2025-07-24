package net.xmx.vortex.physics.constraint.serializer;

import com.github.stephengold.joltjni.ConeConstraint;
import com.github.stephengold.joltjni.ConeConstraintSettings;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.vortex.physics.constraint.builder.ConeConstraintBuilder;
import net.xmx.vortex.physics.constraint.serializer.base.ConstraintSerializer;
import net.xmx.vortex.physics.constraint.util.VxBufferUtil;

public class ConeConstraintSerializer implements ConstraintSerializer<ConeConstraintBuilder, ConeConstraint, ConeConstraintSettings> {

    @Override
    public String getTypeId() {
        return "vortex:cone";
    }

    @Override
    public void serializeSettings(ConeConstraintBuilder builder, FriendlyByteBuf buf) {
        ConeConstraintSettings settings = builder.getSettings();
        buf.writeEnum(settings.getSpace());
        VxBufferUtil.putRVec3(buf, settings.getPoint1());
        VxBufferUtil.putRVec3(buf, settings.getPoint2());
        VxBufferUtil.putVec3(buf, settings.getTwistAxis1());
        VxBufferUtil.putVec3(buf, settings.getTwistAxis2());
        buf.writeFloat(settings.getHalfConeAngle());
    }

    @Override
    public ConeConstraintSettings createSettings(FriendlyByteBuf buf) {
        ConeConstraintSettings s = new ConeConstraintSettings();
        s.setSpace(buf.readEnum(EConstraintSpace.class));
        s.setPoint1(VxBufferUtil.getRVec3(buf));
        s.setPoint2(VxBufferUtil.getRVec3(buf));
        s.setTwistAxis1(VxBufferUtil.getVec3(buf));
        s.setTwistAxis2(VxBufferUtil.getVec3(buf));
        s.setHalfConeAngle(buf.readFloat());
        return s;
    }
}