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
    public void serialize(GearConstraintBuilder builder, FriendlyByteBuf buf) {
        buf.writeUUID(builder.getDependencyConstraintId1() != null ? builder.getDependencyConstraintId1() : WORLD_BODY_ID);
        buf.writeUUID(builder.getDependencyConstraintId2() != null ? builder.getDependencyConstraintId2() : WORLD_BODY_ID);
        buf.writeEnum(builder.space);
        BufferUtil.putVec3(buf, builder.hingeAxis1);
        BufferUtil.putVec3(buf, builder.hingeAxis2);
        buf.writeFloat(builder.ratio);
    }

    @Override
    public GearConstraintSettings createSettings(FriendlyByteBuf buf) {
        // Dependencies are handled by the manager, just read past them here.
        buf.readUUID(); // dep1
        buf.readUUID(); // dep2

        GearConstraintSettings s = new GearConstraintSettings();
        s.setSpace(buf.readEnum(EConstraintSpace.class));
        s.setHingeAxis1(BufferUtil.getVec3(buf));
        s.setHingeAxis2(BufferUtil.getVec3(buf));
        s.setRatio(buf.readFloat());
        return s;
    }

    @Override
    public void applyLiveState(GearConstraint constraint, FriendlyByteBuf buf) {
        // GearConstraint's primary setup is done via setConstraints, which is handled in the manager.
    }
}