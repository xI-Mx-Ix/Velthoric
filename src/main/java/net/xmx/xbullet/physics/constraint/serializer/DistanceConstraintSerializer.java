package net.xmx.xbullet.physics.constraint.serializer;

import com.github.stephengold.joltjni.DistanceConstraint;
import com.github.stephengold.joltjni.DistanceConstraintSettings;
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
    public void serialize(DistanceConstraintBuilder builder, FriendlyByteBuf buf) {
        serializeBodies(builder, buf);
        buf.writeEnum(builder.space);
        BufferUtil.putRVec3(buf, builder.point1);
        BufferUtil.putRVec3(buf, builder.point2);
        buf.writeFloat(builder.minDistance);
        buf.writeFloat(builder.maxDistance);
        BufferUtil.putSpringSettings(buf, builder.limitsSpringSettings);
    }

    @Override
    public DistanceConstraintSettings createSettings(FriendlyByteBuf buf) {
        DistanceConstraintSettings s = new DistanceConstraintSettings();
        s.setSpace(buf.readEnum(EConstraintSpace.class));
        s.setPoint1(BufferUtil.getRVec3(buf));
        s.setPoint2(BufferUtil.getRVec3(buf));
        s.setMinDistance(buf.readFloat());
        s.setMaxDistance(buf.readFloat());
        BufferUtil.loadSpringSettings(buf, s.getLimitsSpringSettings());
        return s;
    }

    @Override
    public void applyLiveState(DistanceConstraint constraint, FriendlyByteBuf buf) {
        // DistanceConstraint has no live state to apply after creation based on JoltJNI.
    }
}