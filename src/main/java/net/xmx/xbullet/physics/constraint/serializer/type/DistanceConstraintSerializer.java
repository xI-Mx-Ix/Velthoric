package net.xmx.xbullet.physics.constraint.serializer.type;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import net.minecraft.nbt.CompoundTag;
import net.xmx.xbullet.physics.constraint.manager.ConstraintManager;
import net.xmx.xbullet.physics.constraint.serializer.IConstraintSerializer;
import net.xmx.xbullet.physics.constraint.util.NbtUtil;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.PhysicsObjectManager;

import java.util.concurrent.CompletableFuture;

public class DistanceConstraintSerializer implements IConstraintSerializer<DistanceConstraint> {

    @Override
    public void save(DistanceConstraint constraint, CompoundTag tag) {
        try (DistanceConstraintSettings settings = (DistanceConstraintSettings) constraint.getConstraintSettings().getPtr()) {
            tag.putString("space", settings.getSpace().name());
            NbtUtil.putRVec3(tag, "point1", settings.getPoint1());
            NbtUtil.putRVec3(tag, "point2", settings.getPoint2());
            tag.putFloat("minDistance", settings.getMinDistance());
            tag.putFloat("maxDistance", settings.getMaxDistance());
            NbtUtil.putSpringSettings(tag, "limitsSpringSettings", settings.getLimitsSpringSettings());
        }
    }

    @Override
    public CompletableFuture<TwoBodyConstraint> createAndLink(CompoundTag tag, ConstraintManager constraintManager, PhysicsObjectManager objectManager) {
        return createFromLoadedBodies(tag, objectManager, (b1, b2, t) -> {
            try (DistanceConstraintSettings settings = new DistanceConstraintSettings()) {
                settings.setSpace(EConstraintSpace.valueOf(t.getString("space")));
                settings.setPoint1(NbtUtil.getRVec3(t, "point1"));
                settings.setPoint2(NbtUtil.getRVec3(t, "point2"));
                settings.setMinDistance(t.getFloat("minDistance"));
                settings.setMaxDistance(t.getFloat("maxDistance"));
                NbtUtil.loadSpringSettings(t, "limitsSpringSettings", settings.getLimitsSpringSettings());
                return (DistanceConstraint) settings.create(b1, b2);
            }
        });
    }
}