package net.xmx.xbullet.physics.constraint.serializer.type;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import net.minecraft.nbt.CompoundTag;
import net.xmx.xbullet.physics.constraint.manager.ConstraintManager;
import net.xmx.xbullet.physics.constraint.serializer.IConstraintSerializer;
import net.xmx.xbullet.physics.constraint.util.NbtUtil;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.ObjectManager;

import java.util.concurrent.CompletableFuture;

public class PointConstraintSerializer implements IConstraintSerializer<PointConstraint> {

    @Override
    public void save(PointConstraint constraint, CompoundTag tag) {
        try (PointConstraintSettings settings = (PointConstraintSettings) constraint.getConstraintSettings().getPtr()) {
            tag.putString("space", settings.getSpace().name());
            NbtUtil.putRVec3(tag, "point1", settings.getPoint1());
            NbtUtil.putRVec3(tag, "point2", settings.getPoint2());
        }
    }

    @Override
    public CompletableFuture<TwoBodyConstraint> createAndLink(CompoundTag tag, ConstraintManager constraintManager, ObjectManager objectManager) {
        return createFromLoadedBodies(tag, objectManager, (bodyInterface, b1Id, b2Id, t) -> {
            PointConstraintSettings settings = new PointConstraintSettings();
            try {
                settings.setSpace(EConstraintSpace.valueOf(t.getString("space")));
                settings.setPoint1(NbtUtil.getRVec3(t, "point1"));
                settings.setPoint2(NbtUtil.getRVec3(t, "point2"));
                return (PointConstraint) bodyInterface.createConstraint(settings, b1Id, b2Id);
            } finally {
                settings.close();
            }
        });
    }
}