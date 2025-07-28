package net.xmx.vortex.physics.constraint.serializer;

import com.github.stephengold.joltjni.DistanceConstraint;
import com.github.stephengold.joltjni.DistanceConstraintSettings;
import com.github.stephengold.joltjni.SpringSettings;
import com.github.stephengold.joltjni.TwoBodyConstraint;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.vortex.physics.constraint.builder.DistanceConstraintBuilder;
import net.xmx.vortex.physics.constraint.serializer.base.ConstraintSerializer;
import net.xmx.vortex.physics.constraint.util.VxBufferUtil;

public class DistanceConstraintSerializer implements ConstraintSerializer<DistanceConstraintBuilder, DistanceConstraint, DistanceConstraintSettings> {

    @Override
    public String getTypeId() {
        return "vortex:distance";
    }

    @Override
    public void serializeSettings(DistanceConstraintBuilder builder, FriendlyByteBuf buf) {
        DistanceConstraintSettings settings = builder.getSettings();
        buf.writeEnum(settings.getSpace());
        VxBufferUtil.putRVec3(buf, settings.getPoint1());
        VxBufferUtil.putRVec3(buf, settings.getPoint2());
        buf.writeFloat(settings.getMinDistance());
        buf.writeFloat(settings.getMaxDistance());
        try (SpringSettings spring = settings.getLimitsSpringSettings()) {
            VxBufferUtil.putSpringSettings(buf, spring);
        }
    }

    @Override
    public DistanceConstraintSettings createSettings(FriendlyByteBuf buf) {
        DistanceConstraintSettings s = new DistanceConstraintSettings();
        s.setSpace(buf.readEnum(EConstraintSpace.class));
        s.setPoint1(VxBufferUtil.getRVec3(buf));
        s.setPoint2(VxBufferUtil.getRVec3(buf));
        s.setMinDistance(buf.readFloat());
        s.setMaxDistance(buf.readFloat());
        try (SpringSettings spring = s.getLimitsSpringSettings()) {
            VxBufferUtil.loadSpringSettings(buf, spring);
        }
        return s;
    }

    @Override
    public void serializeLiveState(TwoBodyConstraint constraint, FriendlyByteBuf buf) {
        if (constraint instanceof DistanceConstraint distance) {
            try (SpringSettings spring = distance.getLimitsSpringSettings()) {
                VxBufferUtil.putSpringSettings(buf, spring);
            }
        }
    }

    @Override
    public void applyLiveState(TwoBodyConstraint constraint, FriendlyByteBuf buf) {
        if (constraint instanceof DistanceConstraint distance) {
            try (SpringSettings spring = distance.getLimitsSpringSettings()) {
                VxBufferUtil.loadSpringSettings(buf, spring);
            }
        }
    }
}