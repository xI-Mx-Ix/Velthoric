package net.xmx.xbullet.physics.constraint.serializer.type;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import net.minecraft.nbt.CompoundTag;
import net.xmx.xbullet.physics.constraint.manager.ConstraintManager;
import net.xmx.xbullet.physics.constraint.serializer.IConstraintSerializer;
import net.xmx.xbullet.physics.constraint.util.NbtUtil;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.ObjectManager;

import java.util.concurrent.CompletableFuture;

public class PulleyConstraintSerializer implements IConstraintSerializer<PulleyConstraint> {

    @Override
    public void save(PulleyConstraint constraint, CompoundTag tag) {
        try (PulleyConstraintSettings settings = (PulleyConstraintSettings) constraint.getConstraintSettings().getPtr()) {
            tag.putString("space", settings.getSpace().name());
            NbtUtil.putRVec3(tag, "bodyPoint1", settings.getBodyPoint1());
            NbtUtil.putRVec3(tag, "bodyPoint2", settings.getBodyPoint2());
            NbtUtil.putRVec3(tag, "fixedPoint1", settings.getFixedPoint1());
            NbtUtil.putRVec3(tag, "fixedPoint2", settings.getFixedPoint2());
            tag.putFloat("ratio", settings.getRatio());
            tag.putFloat("minLength", settings.getMinLength());
            tag.putFloat("maxLength", settings.getMaxLength());
        }
    }

    @Override
    public CompletableFuture<TwoBodyConstraint> createAndLink(CompoundTag tag, ConstraintManager constraintManager, ObjectManager objectManager) {
        return createFromLoadedBodies(tag, objectManager, (bodyInterface, b1Id, b2Id, t) -> {
            PulleyConstraintSettings settings = new PulleyConstraintSettings();
            try {
                settings.setSpace(EConstraintSpace.valueOf(t.getString("space")));
                settings.setBodyPoint1(NbtUtil.getRVec3(t, "bodyPoint1"));
                settings.setBodyPoint2(NbtUtil.getRVec3(t, "bodyPoint2"));
                settings.setFixedPoint1(NbtUtil.getRVec3(t, "fixedPoint1"));
                settings.setFixedPoint2(NbtUtil.getRVec3(t, "fixedPoint2"));
                settings.setRatio(t.getFloat("ratio"));
                settings.setMinLength(t.getFloat("minLength"));
                settings.setMaxLength(t.getFloat("maxLength"));
                return (PulleyConstraint) bodyInterface.createConstraint(settings, b1Id, b2Id);
            } finally {
                settings.close();
            }
        });
    }
}