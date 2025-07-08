package net.xmx.xbullet.physics.constraint.serializer.type;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import net.minecraft.nbt.CompoundTag;
import net.xmx.xbullet.physics.constraint.IConstraint;
import net.xmx.xbullet.physics.constraint.manager.ConstraintManager;
import net.xmx.xbullet.physics.constraint.serializer.IConstraintSerializer;
import net.xmx.xbullet.physics.constraint.util.NbtUtil;
import net.xmx.xbullet.physics.object.physicsobject.manager.ObjectManager;
import net.xmx.xbullet.physics.world.PhysicsWorld;

import javax.annotation.Nullable;
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
    public CompletableFuture<TwoBodyConstraint> createAndLink(CompoundTag tag, ConstraintManager constraintManager, ObjectManager objectManager) {
        UUID[] dependentConstraintIds = loadDependentConstraintIds(tag);
        if (dependentConstraintIds == null) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<IConstraint> futureHinge = constraintManager.getOrLoadConstraint(dependentConstraintIds[0]);
        CompletableFuture<IConstraint> futureSlider = constraintManager.getOrLoadConstraint(dependentConstraintIds[1]);
        PhysicsWorld physicsWorld = objectManager.getPhysicsWorld();
        if (physicsWorld == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("PhysicsWorld is not available"));
        }

        return CompletableFuture.allOf(futureHinge, futureSlider).thenApplyAsync(v -> {
            IConstraint depHinge = futureHinge.join();
            IConstraint depSlider = futureSlider.join();
            if (depHinge == null || depSlider == null) {
                return null;
            }

            BodyInterface bodyInterface = physicsWorld.getBodyInterface();
            if(bodyInterface == null) {
                return null;
            }

            RackAndPinionConstraintSettings settings = new RackAndPinionConstraintSettings();
            try {
                settings.setSpace(EConstraintSpace.valueOf(tag.getString("space")));
                settings.setHingeAxis(NbtUtil.getVec3(tag, "hingeAxis"));
                settings.setSliderAxis(NbtUtil.getVec3(tag, "sliderAxis"));
                float rackLengthPerTooth = Math.abs(tag.getFloat("ratio"));
                settings.setRatio(1, rackLengthPerTooth, 1);

                int bodyIdHinge = depHinge.getJoltConstraint().getBody1().getId();
                int bodyIdSlider = depSlider.getJoltConstraint().getBody1().getId();

                RackAndPinionConstraint constraint = (RackAndPinionConstraint) bodyInterface.createConstraint(settings, bodyIdHinge, bodyIdSlider);
                if (constraint != null) {
                    constraint.setConstraints(depHinge.getJoltConstraint(), depSlider.getJoltConstraint());
                }
                return constraint;
            } finally {
                settings.close();
            }
        }, physicsWorld);
    }

    @Nullable
    public UUID[] loadDependentConstraintIds(CompoundTag tag) {
        if (tag.hasUUID("hingeConstraintId") && tag.hasUUID("sliderConstraintId")) {
            return new UUID[]{tag.getUUID("hingeConstraintId"), tag.getUUID("sliderConstraintId")};
        }
        return null;
    }
}