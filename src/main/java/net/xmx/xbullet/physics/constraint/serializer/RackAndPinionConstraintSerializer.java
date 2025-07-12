package net.xmx.xbullet.physics.constraint.serializer;

import com.github.stephengold.joltjni.RackAndPinionConstraint;
import com.github.stephengold.joltjni.RackAndPinionConstraintSettings;
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
    public void serialize(RackAndPinionConstraintBuilder builder, FriendlyByteBuf buf) {
        buf.writeUUID(builder.getDependencyConstraintId1() != null ? builder.getDependencyConstraintId1() : WORLD_BODY_ID);
        buf.writeUUID(builder.getDependencyConstraintId2() != null ? builder.getDependencyConstraintId2() : WORLD_BODY_ID);
        buf.writeEnum(builder.space);
        BufferUtil.putVec3(buf, builder.hingeAxis);
        BufferUtil.putVec3(buf, builder.sliderAxis);
        buf.writeFloat(builder.ratio);
    }

    @Override
    public RackAndPinionConstraintSettings createSettings(FriendlyByteBuf buf) {
        // Dependencies are handled by the manager.
        buf.readUUID(); // dep1
        buf.readUUID(); // dep2

        RackAndPinionConstraintSettings s = new RackAndPinionConstraintSettings();
        s.setSpace(buf.readEnum(EConstraintSpace.class));
        s.setHingeAxis(BufferUtil.getVec3(buf));
        s.setSliderAxis(BufferUtil.getVec3(buf));

        // This specific setting doesn't exist on the settings object, it's calculated internally
        // by Jolt based on the linked hinge/slider constraints. We read it to advance the buffer.
        buf.readFloat();

        return s;
    }

    @Override
    public void applyLiveState(RackAndPinionConstraint constraint, FriendlyByteBuf buf) {
        // RackAndPinionConstraint's primary setup is done via setConstraints, which is handled in the manager.
    }
}