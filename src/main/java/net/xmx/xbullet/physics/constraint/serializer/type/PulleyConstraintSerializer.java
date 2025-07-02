package net.xmx.xbullet.physics.constraint.serializer.type;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import net.minecraft.nbt.CompoundTag;
import net.xmx.xbullet.physics.constraint.manager.ConstraintManager;
import net.xmx.xbullet.physics.constraint.serializer.IConstraintSerializer;
import net.xmx.xbullet.physics.constraint.util.NbtUtil;
import net.xmx.xbullet.physics.object.global.physicsobject.IPhysicsObject;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.PhysicsObjectManager;
import net.xmx.xbullet.physics.physicsworld.PhysicsWorld;

import java.util.UUID;
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
    public CompletableFuture<TwoBodyConstraint> createAndLink(CompoundTag tag, ConstraintManager constraintManager, PhysicsObjectManager objectManager) {
        UUID[] bodyIds = loadBodyIds(tag);
        if (bodyIds == null) return CompletableFuture.completedFuture(null);

        CompletableFuture<IPhysicsObject> future1 = objectManager.getOrLoadObject(bodyIds[0]);
        CompletableFuture<IPhysicsObject> future2 = objectManager.getOrLoadObject(bodyIds[1]);
        PhysicsWorld physicsWorld = objectManager.getPhysicsWorld();

        return CompletableFuture.allOf(future1, future2).thenApplyAsync(v -> {
            IPhysicsObject obj1 = future1.join();
            IPhysicsObject obj2 = future2.join();
            if (obj1 == null || obj2 == null || physicsWorld == null) return null;

            int bodyId1 = obj1.getBodyId();
            int bodyId2 = obj2.getBodyId();

            BodyInterface bodyInterface = physicsWorld.getBodyInterface();
            if (bodyInterface == null) return null;

            Body b1 = new Body(bodyId1);
            Body b2 = (bodyInterface.getMotionType(bodyId2) == EMotionType.Static) ? Body.sFixedToWorld() : new Body(bodyId2);

            try (PulleyConstraintSettings settings = new PulleyConstraintSettings()) {
                settings.setSpace(EConstraintSpace.valueOf(tag.getString("space")));
                settings.setBodyPoint1(NbtUtil.getRVec3(tag, "bodyPoint1"));
                settings.setBodyPoint2(NbtUtil.getRVec3(tag, "bodyPoint2"));
                settings.setFixedPoint1(NbtUtil.getRVec3(tag, "fixedPoint1"));
                settings.setFixedPoint2(NbtUtil.getRVec3(tag, "fixedPoint2"));
                settings.setRatio(tag.getFloat("ratio"));
                settings.setMinLength(tag.getFloat("minLength"));
                settings.setMaxLength(tag.getFloat("maxLength"));

                return settings.create(b1, b2);
            }
        });
    }
}