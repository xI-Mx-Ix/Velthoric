package net.xmx.xbullet.physics.constraint.serializer;

import com.github.stephengold.joltjni.RackAndPinionConstraint;
import com.github.stephengold.joltjni.RackAndPinionConstraintSettings;
import com.github.stephengold.joltjni.TwoBodyConstraint;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.xbullet.physics.constraint.builder.RackAndPinionConstraintBuilder;
import net.xmx.xbullet.physics.constraint.serializer.base.ConstraintSerializer;
import net.xmx.xbullet.physics.constraint.util.BufferUtil;

public class RackAndPinionConstraintSerializer implements ConstraintSerializer<RackAndPinionConstraintBuilder, RackAndPinionConstraint, RackAndPinionConstraintSettings> {

    @Override
    public String getTypeId() {
        return "xbullet:rack_and_pinion";
    }

    @Override
    public void serializeSettings(RackAndPinionConstraintBuilder builder, FriendlyByteBuf buf) {
        RackAndPinionConstraintSettings settings = builder.getSettings();
        buf.writeEnum(settings.getSpace());
        BufferUtil.putVec3(buf, settings.getHingeAxis());
        BufferUtil.putVec3(buf, settings.getSliderAxis());
        buf.writeFloat(settings.getRatio());
    }

    @Override
    public RackAndPinionConstraintSettings createSettings(FriendlyByteBuf buf) {
        RackAndPinionConstraintSettings s = new RackAndPinionConstraintSettings();
        s.setSpace(buf.readEnum(EConstraintSpace.class));
        s.setHingeAxis(BufferUtil.getVec3(buf));
        s.setSliderAxis(BufferUtil.getVec3(buf));
        s.setRatio(1, 1.0f, 1);
        return s;
    }
}