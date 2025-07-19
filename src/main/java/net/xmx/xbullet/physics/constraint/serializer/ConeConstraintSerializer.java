package net.xmx.xbullet.physics.constraint.serializer;

import com.github.stephengold.joltjni.ConeConstraint;
import com.github.stephengold.joltjni.ConeConstraintSettings;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.xbullet.physics.constraint.builder.ConeConstraintBuilder;
import net.xmx.xbullet.physics.constraint.serializer.base.ConstraintSerializer;
import net.xmx.xbullet.physics.constraint.util.BufferUtil;

public class ConeConstraintSerializer implements ConstraintSerializer<ConeConstraintBuilder, ConeConstraint, ConeConstraintSettings> {

    @Override
    public String getTypeId() {
        return "xbullet:cone";
    }

    @Override
    public void serializeSettings(ConeConstraintBuilder builder, FriendlyByteBuf buf) {
        ConeConstraintSettings settings = builder.getSettings();
        buf.writeEnum(settings.getSpace());
        BufferUtil.putRVec3(buf, settings.getPoint1());
        BufferUtil.putRVec3(buf, settings.getPoint2());
        BufferUtil.putVec3(buf, settings.getTwistAxis1());
        BufferUtil.putVec3(buf, settings.getTwistAxis2());
        buf.writeFloat(settings.getHalfConeAngle());
    }

    @Override
    public ConeConstraintSettings createSettings(FriendlyByteBuf buf) {
        ConeConstraintSettings s = new ConeConstraintSettings();
        s.setSpace(buf.readEnum(EConstraintSpace.class));
        s.setPoint1(BufferUtil.getRVec3(buf));
        s.setPoint2(BufferUtil.getRVec3(buf));
        s.setTwistAxis1(BufferUtil.getVec3(buf));
        s.setTwistAxis2(BufferUtil.getVec3(buf));
        s.setHalfConeAngle(buf.readFloat());
        return s;
    }
}