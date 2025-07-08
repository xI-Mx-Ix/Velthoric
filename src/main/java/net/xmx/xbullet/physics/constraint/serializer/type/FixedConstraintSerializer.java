package net.xmx.xbullet.physics.constraint.serializer.type;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import net.minecraft.nbt.CompoundTag;
import net.xmx.xbullet.physics.constraint.manager.ConstraintManager;
import net.xmx.xbullet.physics.constraint.serializer.IConstraintSerializer;
import net.xmx.xbullet.physics.constraint.util.NbtUtil;
import net.xmx.xbullet.physics.object.physicsobject.manager.ObjectManager;

import java.util.concurrent.CompletableFuture;

public class FixedConstraintSerializer implements IConstraintSerializer<FixedConstraint> {

    @Override
    public void save(FixedConstraint constraint, CompoundTag tag) {
        try (FixedConstraintSettings settings = (FixedConstraintSettings) constraint.getConstraintSettings().getPtr()) {
            tag.putString("space", settings.getSpace().name());
            tag.putBoolean("autoDetectPoint", settings.getAutoDetectPoint());
            NbtUtil.putRVec3(tag, "point1", settings.getPoint1());
            NbtUtil.putRVec3(tag, "point2", settings.getPoint2());
            NbtUtil.putVec3(tag, "axisX1", settings.getAxisX1());
            NbtUtil.putVec3(tag, "axisY1", settings.getAxisY1());
            NbtUtil.putVec3(tag, "axisX2", settings.getAxisX2());
            NbtUtil.putVec3(tag, "axisY2", settings.getAxisY2());
        }
    }

    @Override
    public CompletableFuture<TwoBodyConstraint> createAndLink(CompoundTag tag, ConstraintManager constraintManager, ObjectManager objectManager) {
        return createFromLoadedBodies(tag, objectManager, (bodyInterface, b1Id, b2Id, t) -> {
            FixedConstraintSettings settings = new FixedConstraintSettings();
            try {
                settings.setSpace(EConstraintSpace.valueOf(t.getString("space")));
                settings.setAutoDetectPoint(t.getBoolean("autoDetectPoint"));
                settings.setPoint1(NbtUtil.getRVec3(t, "point1"));
                settings.setPoint2(NbtUtil.getRVec3(t, "point2"));
                settings.setAxisX1(NbtUtil.getVec3(t, "axisX1"));
                settings.setAxisY1(NbtUtil.getVec3(t, "axisY1"));
                settings.setAxisX2(NbtUtil.getVec3(t, "axisX2"));
                settings.setAxisY2(NbtUtil.getVec3(t, "axisY2"));
                return (FixedConstraint) bodyInterface.createConstraint(settings, b1Id, b2Id);
            } finally {
                settings.close();
            }
        });
    }
}