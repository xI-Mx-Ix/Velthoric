package net.xmx.xbullet.physics.constraint.serializer;

import com.github.stephengold.joltjni.GearConstraint;
import com.github.stephengold.joltjni.GearConstraintSettings;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.xbullet.physics.constraint.builder.GearConstraintBuilder;
import net.xmx.xbullet.physics.constraint.serializer.base.ConstraintSerializer;
import net.xmx.xbullet.physics.constraint.util.BufferUtil;

public class GearConstraintSerializer implements ConstraintSerializer<GearConstraintBuilder, GearConstraint, GearConstraintSettings> {

    @Override
    public String getTypeId() {
        return "xbullet:gear";
    }

    @Override
    public void serializeSettings(GearConstraintBuilder builder, FriendlyByteBuf buf) {
        GearConstraintSettings settings = builder.getSettings();
        buf.writeEnum(settings.getSpace());
        BufferUtil.putVec3(buf, settings.getHingeAxis1());
        BufferUtil.putVec3(buf, settings.getHingeAxis2());
        buf.writeFloat(settings.getRatio());
    }

    @Override
    public GearConstraintSettings createSettings(FriendlyByteBuf buf) {
        GearConstraintSettings s = new GearConstraintSettings();
        s.setSpace(buf.readEnum(EConstraintSpace.class));
        s.setHingeAxis1(BufferUtil.getVec3(buf));
        s.setHingeAxis2(BufferUtil.getVec3(buf));
        s.setRatio(buf.readFloat());
        return s;
    }
}