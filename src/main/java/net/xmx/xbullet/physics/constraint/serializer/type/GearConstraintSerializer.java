package net.xmx.xbullet.physics.constraint.serializer.type;

import com.github.stephengold.joltjni.GearConstraint;
import com.github.stephengold.joltjni.GearConstraintSettings;
import com.github.stephengold.joltjni.TwoBodyConstraint;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import net.minecraft.nbt.CompoundTag;
import net.xmx.xbullet.physics.constraint.IConstraint;
import net.xmx.xbullet.physics.constraint.manager.ConstraintManager;
import net.xmx.xbullet.physics.constraint.serializer.IConstraintSerializer;
import net.xmx.xbullet.physics.constraint.util.NbtUtil;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.PhysicsObjectManager;
import net.xmx.xbullet.physics.world.PhysicsWorld;

import javax.annotation.Nullable;
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
        UUID[] dependentConstraintIds = loadDependentConstraintIds(tag);
        if (dependentConstraintIds == null) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<IConstraint> futureConstraint1 = constraintManager.getOrLoadConstraint(dependentConstraintIds[0]);
        CompletableFuture<IConstraint> futureConstraint2 = constraintManager.getOrLoadConstraint(dependentConstraintIds[1]);
        PhysicsWorld physicsWorld = objectManager.getPhysicsWorld();
        if (physicsWorld == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("PhysicsWorld is not available"));
        }

        return CompletableFuture.allOf(futureConstraint1, futureConstraint2).thenApplyAsync(v -> {
            IConstraint dep1 = futureConstraint1.join();
            IConstraint dep2 = futureConstraint2.join();
            if (dep1 == null || dep2 == null) {
                return null;
            }

            try (GearConstraintSettings settings = new GearConstraintSettings()) {
                settings.setSpace(EConstraintSpace.valueOf(tag.getString("space")));
                settings.setHingeAxis1(NbtUtil.getVec3(tag, "hingeAxis1"));
                settings.setHingeAxis2(NbtUtil.getVec3(tag, "hingeAxis2"));
                settings.setRatio(tag.getFloat("ratio"));
                GearConstraint constraint = (GearConstraint) settings.create(dep1.getJoltConstraint().getBody1(), dep1.getJoltConstraint().getBody2());
                if (constraint != null) {
                    constraint.setConstraints(dep1.getJoltConstraint(), dep2.getJoltConstraint());
                }
                return constraint;
            }
        }, physicsWorld);
    }

    @Nullable
    public UUID[] loadDependentConstraintIds(CompoundTag tag) {
        if (tag.hasUUID("constraintId1") && tag.hasUUID("constraintId2")) {
            return new UUID[]{tag.getUUID("constraintId1"), tag.getUUID("constraintId2")};
        }
        return null;
    }
}