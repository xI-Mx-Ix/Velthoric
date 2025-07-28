package net.xmx.vortex.physics.constraint.serializer;

import com.github.stephengold.joltjni.GearConstraint;
import com.github.stephengold.joltjni.GearConstraintSettings;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.vortex.physics.constraint.builder.GearConstraintBuilder;
import net.xmx.vortex.physics.constraint.serializer.base.ConstraintSerializer;
import net.xmx.vortex.physics.constraint.util.VxBufferUtil;

public class GearConstraintSerializer implements ConstraintSerializer<GearConstraintBuilder, GearConstraint, GearConstraintSettings> {

    @Override
    public String getTypeId() {
        return "vortex:gear";
    }

    @Override
    public void serializeSettings(GearConstraintBuilder builder, FriendlyByteBuf buf) {
        GearConstraintSettings settings = builder.getSettings();
        buf.writeEnum(settings.getSpace());
        VxBufferUtil.putVec3(buf, settings.getHingeAxis1());
        VxBufferUtil.putVec3(buf, settings.getHingeAxis2());
        buf.writeFloat(settings.getRatio());
    }

    @Override
    public GearConstraintSettings createSettings(FriendlyByteBuf buf) {
        GearConstraintSettings s = new GearConstraintSettings();
        s.setSpace(buf.readEnum(EConstraintSpace.class));
        s.setHingeAxis1(VxBufferUtil.getVec3(buf));
        s.setHingeAxis2(VxBufferUtil.getVec3(buf));
        s.setRatio(buf.readFloat());
        return s;
    }
}