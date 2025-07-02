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

            try (PointConstraintSettings settings = new PointConstraintSettings()) {
                settings.setSpace(EConstraintSpace.valueOf(tag.getString("space")));
                settings.setPoint1(NbtUtil.getRVec3(tag, "point1"));
                settings.setPoint2(NbtUtil.getRVec3(tag, "point2"));

                return settings.create(b1, b2);
            }
        });
    }
}