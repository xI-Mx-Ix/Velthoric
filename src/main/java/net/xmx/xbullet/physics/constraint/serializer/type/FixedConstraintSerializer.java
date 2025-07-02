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

            try (FixedConstraintSettings settings = new FixedConstraintSettings()) {
                settings.setSpace(EConstraintSpace.valueOf(tag.getString("space")));
                settings.setAutoDetectPoint(tag.getBoolean("autoDetectPoint"));
                settings.setPoint1(NbtUtil.getRVec3(tag, "point1"));
                settings.setPoint2(NbtUtil.getRVec3(tag, "point2"));
                settings.setAxisX1(NbtUtil.getVec3(tag, "axisX1"));
                settings.setAxisY1(NbtUtil.getVec3(tag, "axisY1"));
                settings.setAxisX2(NbtUtil.getVec3(tag, "axisX2"));
                settings.setAxisY2(NbtUtil.getVec3(tag, "axisY2"));

                return settings.create(b1, b2);
            }
        });
    }
}