package net.xmx.xbullet.physics.constraint.serializer;

import com.github.stephengold.joltjni.DistanceConstraint;
import com.github.stephengold.joltjni.DistanceConstraintSettings;
import com.github.stephengold.joltjni.SpringSettings;
import com.github.stephengold.joltjni.TwoBodyConstraint;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.xbullet.physics.constraint.builder.DistanceConstraintBuilder;
import net.xmx.xbullet.physics.constraint.serializer.base.ConstraintSerializer;
import net.xmx.xbullet.physics.constraint.util.BufferUtil;

public class DistanceConstraintSerializer implements ConstraintSerializer<DistanceConstraintBuilder, DistanceConstraint, DistanceConstraintSettings> {

    @Override
    public String getTypeId() {
        return "xbullet:distance";
    }

    @Override
    public void serializeSettings(DistanceConstraintBuilder builder, FriendlyByteBuf buf) {
        DistanceConstraintSettings settings = builder.getSettings();
        buf.writeEnum(settings.getSpace());
        BufferUtil.putRVec3(buf, settings.getPoint1());
        BufferUtil.putRVec3(buf, settings.getPoint2());
        buf.writeFloat(settings.getMinDistance());
        buf.writeFloat(settings.getMaxDistance());
        try (SpringSettings spring = settings.getLimitsSpringSettings()) {
            BufferUtil.putSpringSettings(buf, spring);
        }
    }

    @Override
    public DistanceConstraintSettings createSettings(FriendlyByteBuf buf) {
        DistanceConstraintSettings s = new DistanceConstraintSettings();
        s.setSpace(buf.readEnum(EConstraintSpace.class));
        s.setPoint1(BufferUtil.getRVec3(buf));
        s.setPoint2(BufferUtil.getRVec3(buf));
        s.setMinDistance(buf.readFloat());
        s.setMaxDistance(buf.readFloat());
        try (SpringSettings spring = s.getLimitsSpringSettings()) {
            BufferUtil.loadSpringSettings(buf, spring);
        }
        return s;
    }

    @Override
    public void serializeLiveState(TwoBodyConstraint constraint, FriendlyByteBuf buf) {
        if (constraint instanceof DistanceConstraint distance) {
            try (SpringSettings spring = distance.getLimitsSpringSettings()) {
                BufferUtil.putSpringSettings(buf, spring);
            }
        }
    }

    @Override
    public void applyLiveState(TwoBodyConstraint constraint, FriendlyByteBuf buf) {
        if (constraint instanceof DistanceConstraint distance) {
            try (SpringSettings spring = distance.getLimitsSpringSettings()) {
                BufferUtil.loadSpringSettings(buf, spring);
            }
        }
    }
}