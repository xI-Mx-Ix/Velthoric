package net.xmx.xbullet.physics.constraint.serializer.type;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import net.minecraft.nbt.CompoundTag;
import net.xmx.xbullet.physics.constraint.IConstraint;
import net.xmx.xbullet.physics.constraint.manager.ConstraintManager;
import net.xmx.xbullet.physics.constraint.serializer.IConstraintSerializer;
import net.xmx.xbullet.physics.constraint.util.NbtUtil;
import net.xmx.xbullet.physics.object.global.physicsobject.IPhysicsObject;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.PhysicsObjectManager;
import net.xmx.xbullet.physics.physicsworld.PhysicsWorld;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class GearConstraintSerializer implements IConstraintSerializer<GearConstraint> {

    @Override
    public void save(GearConstraint constraint, CompoundTag tag) {
        try (GearConstraintSettings settings = (GearConstraintSettings) constraint.getConstraintSettings().getPtr()) {
            tag.putString("space", settings.getSpace().name());
            NbtUtil.putVec3(tag, "hingeAxis1", settings.getHingeAxis1());
            NbtUtil.putVec3(tag, "hingeAxis2", settings.getHingeAxis2());
            tag.putFloat("ratio", settings.getRatio());
        }
    }

    @Override
    public CompletableFuture<TwoBodyConstraint> createAndLink(CompoundTag tag, ConstraintManager constraintManager, PhysicsObjectManager objectManager) {
        UUID[] bodyIds = loadBodyIds(tag);
        UUID[] dependentConstraintIds = loadDependentConstraintIds(tag);
        if (bodyIds == null || dependentConstraintIds == null) return CompletableFuture.completedFuture(null);

        CompletableFuture<IPhysicsObject> futureBody1 = objectManager.getOrLoadObject(bodyIds[0]);
        CompletableFuture<IPhysicsObject> futureBody2 = objectManager.getOrLoadObject(bodyIds[1]);
        CompletableFuture<IConstraint> futureConstraint1 = constraintManager.getOrLoadConstraint(dependentConstraintIds[0]);
        CompletableFuture<IConstraint> futureConstraint2 = constraintManager.getOrLoadConstraint(dependentConstraintIds[1]);
        PhysicsWorld physicsWorld = objectManager.getPhysicsWorld();

        return CompletableFuture.allOf(futureBody1, futureBody2, futureConstraint1, futureConstraint2).thenApplyAsync(v -> {
            IPhysicsObject obj1 = futureBody1.join();
            IPhysicsObject obj2 = futureBody2.join();
            IConstraint depConstraint1 = futureConstraint1.join();
            IConstraint depConstraint2 = futureConstraint2.join();
            if (obj1 == null || obj2 == null || depConstraint1 == null || depConstraint2 == null || physicsWorld == null) return null;

            Body b1 = new Body(obj1.getBodyId());
            Body b2 = new Body(obj2.getBodyId());

            try (GearConstraintSettings settings = new GearConstraintSettings()) {
                settings.setSpace(EConstraintSpace.valueOf(tag.getString("space")));
                settings.setHingeAxis1(NbtUtil.getVec3(tag, "hingeAxis1"));
                settings.setHingeAxis2(NbtUtil.getVec3(tag, "hingeAxis2"));
                settings.setRatio(tag.getFloat("ratio"));

                GearConstraint constraint = (GearConstraint) settings.create(b1, b2);
                if (constraint != null) {
                    constraint.setConstraints(depConstraint1.getJoltConstraint(), depConstraint2.getJoltConstraint());
                }
                return constraint;
            }
        });
    }

    public UUID[] loadDependentConstraintIds(CompoundTag tag) {
        if (tag.hasUUID("constraintId1") && tag.hasUUID("constraintId2")) {
            return new UUID[]{tag.getUUID("constraintId1"), tag.getUUID("constraintId2")};
        }
        return null;
    }
}