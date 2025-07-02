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

public class RackAndPinionConstraintSerializer implements IConstraintSerializer<RackAndPinionConstraint> {

    @Override
    public void save(RackAndPinionConstraint constraint, CompoundTag tag) {
        try (RackAndPinionConstraintSettings settings = (RackAndPinionConstraintSettings) constraint.getConstraintSettings().getPtr()) {
            tag.putString("space", settings.getSpace().name());
            NbtUtil.putVec3(tag, "hingeAxis", settings.getHingeAxis());
            NbtUtil.putVec3(tag, "sliderAxis", settings.getSliderAxis());
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
        CompletableFuture<IConstraint> futureHinge = constraintManager.getOrLoadConstraint(dependentConstraintIds[0]);
        CompletableFuture<IConstraint> futureSlider = constraintManager.getOrLoadConstraint(dependentConstraintIds[1]);
        PhysicsWorld physicsWorld = objectManager.getPhysicsWorld();

        return CompletableFuture.allOf(futureBody1, futureBody2, futureHinge, futureSlider).thenApplyAsync(v -> {
            IPhysicsObject obj1 = futureBody1.join();
            IPhysicsObject obj2 = futureBody2.join();
            IConstraint depHinge = futureHinge.join();
            IConstraint depSlider = futureSlider.join();
            if (obj1 == null || obj2 == null || depHinge == null || depSlider == null || physicsWorld == null) return null;

            Body b1 = new Body(obj1.getBodyId());
            Body b2 = new Body(obj2.getBodyId());

            try (RackAndPinionConstraintSettings settings = new RackAndPinionConstraintSettings()) {
                settings.setSpace(EConstraintSpace.valueOf(tag.getString("space")));
                settings.setHingeAxis(NbtUtil.getVec3(tag, "hingeAxis"));
                settings.setSliderAxis(NbtUtil.getVec3(tag, "sliderAxis"));

                float ratio = tag.getFloat("ratio");
                float rackLengthPerTooth = Math.abs(ratio);
                settings.setRatio(1, rackLengthPerTooth, 1);

                RackAndPinionConstraint constraint = (RackAndPinionConstraint) settings.create(b1, b2);
                if (constraint != null) {
                    constraint.setConstraints(depHinge.getJoltConstraint(), depSlider.getJoltConstraint());
                }
                return constraint;
            }
        });
    }

    public UUID[] loadDependentConstraintIds(CompoundTag tag) {
        if (tag.hasUUID("hingeConstraintId") && tag.hasUUID("sliderConstraintId")) {
            return new UUID[]{tag.getUUID("hingeConstraintId"), tag.getUUID("sliderConstraintId")};
        }
        return null;
    }
}