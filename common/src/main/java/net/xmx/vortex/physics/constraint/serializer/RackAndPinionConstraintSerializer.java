package net.xmx.vortex.physics.constraint.serializer;

import com.github.stephengold.joltjni.RackAndPinionConstraint;
import com.github.stephengold.joltjni.RackAndPinionConstraintSettings;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.vortex.physics.constraint.builder.RackAndPinionConstraintBuilder;
import net.xmx.vortex.physics.constraint.serializer.base.ConstraintSerializer;
import net.xmx.vortex.physics.constraint.util.VxBufferUtil;

public class RackAndPinionConstraintSerializer implements ConstraintSerializer<RackAndPinionConstraintBuilder, RackAndPinionConstraint, RackAndPinionConstraintSettings> {

    @Override
    public String getTypeId() {
        return "vortex:rack_and_pinion";
    }

    @Override
    public void serializeSettings(RackAndPinionConstraintBuilder builder, FriendlyByteBuf buf) {
        RackAndPinionConstraintSettings settings = builder.getSettings();
        buf.writeEnum(settings.getSpace());
        VxBufferUtil.putVec3(buf, settings.getHingeAxis());
        VxBufferUtil.putVec3(buf, settings.getSliderAxis());
        buf.writeFloat(settings.getRatio());
    }

    @Override
    public RackAndPinionConstraintSettings createSettings(FriendlyByteBuf buf) {
        RackAndPinionConstraintSettings s = new RackAndPinionConstraintSettings();
        s.setSpace(buf.readEnum(EConstraintSpace.class));
        s.setHingeAxis(VxBufferUtil.getVec3(buf));
        s.setSliderAxis(VxBufferUtil.getVec3(buf));
        s.setRatio(1, 1.0f, 1);
        return s;
    }
}