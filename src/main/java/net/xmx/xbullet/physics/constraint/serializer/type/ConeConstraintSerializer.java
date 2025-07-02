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

public class ConeConstraintSerializer implements IConstraintSerializer<ConeConstraint> {

    @Override
    public void save(ConeConstraint constraint, CompoundTag tag) {
        try (ConeConstraintSettings settings = (ConeConstraintSettings) constraint.getConstraintSettings().getPtr()) {
            tag.putString("space", settings.getSpace().name());
            NbtUtil.putRVec3(tag, "point1", settings.getPoint1());
            NbtUtil.putRVec3(tag, "point2", settings.getPoint2());
            NbtUtil.putVec3(tag, "twistAxis1", settings.getTwistAxis1());
            NbtUtil.putVec3(tag, "twistAxis2", settings.getTwistAxis2());
            tag.putFloat("halfConeAngle", settings.getHalfConeAngle());
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

            try (ConeConstraintSettings settings = new ConeConstraintSettings()) {
                settings.setSpace(EConstraintSpace.valueOf(tag.getString("space")));
                settings.setPoint1(NbtUtil.getRVec3(tag, "point1"));
                settings.setPoint2(NbtUtil.getRVec3(tag, "point2"));
                settings.setTwistAxis1(NbtUtil.getVec3(tag, "twistAxis1"));
                settings.setTwistAxis2(NbtUtil.getVec3(tag, "twistAxis2"));
                settings.setHalfConeAngle(tag.getFloat("halfConeAngle"));

                return settings.create(b1, b2);
            }
        });
    }
}