package net.xmx.xbullet.physics.constraint.serializer.type;

import com.github.stephengold.joltjni.RackAndPinionConstraint;
import com.github.stephengold.joltjni.RackAndPinionConstraintSettings;
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

            try (RackAndPinionConstraintSettings settings = new RackAndPinionConstraintSettings()) {
                settings.setSpace(EConstraintSpace.valueOf(tag.getString("space")));
                settings.setHingeAxis(NbtUtil.getVec3(tag, "hingeAxis"));
                settings.setSliderAxis(NbtUtil.getVec3(tag, "sliderAxis"));
                float rackLengthPerTooth = Math.abs(tag.getFloat("ratio"));
                settings.setRatio(1, rackLengthPerTooth, 1);
                RackAndPinionConstraint constraint = (RackAndPinionConstraint) settings.create(depHinge.getJoltConstraint().getBody1(), depSlider.getJoltConstraint().getBody1());
                if (constraint != null) {
                    constraint.setConstraints(depHinge.getJoltConstraint(), depSlider.getJoltConstraint());
                }
                return constraint;
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