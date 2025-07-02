package net.xmx.xbullet.physics.constraint.serializer;

import com.github.stephengold.joltjni.TwoBodyConstraint;
import net.minecraft.nbt.CompoundTag;
import net.xmx.xbullet.physics.constraint.manager.ConstraintManager;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.PhysicsObjectManager;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface IConstraintSerializer<T extends TwoBodyConstraint> {

    UUID WORLD_BODY_ID = new UUID(0L, 0L);

    void save(T constraint, CompoundTag tag);

    CompletableFuture<TwoBodyConstraint> createAndLink(CompoundTag tag, ConstraintManager constraintManager, PhysicsObjectManager objectManager);

    default void saveBodyIds(UUID bodyId1, @Nullable UUID bodyId2, CompoundTag tag) {
        tag.putUUID("bodyId1", bodyId1);
        tag.putUUID("bodyId2", bodyId2 != null ? bodyId2 : WORLD_BODY_ID);
    }

    @Nullable
    default UUID[] loadBodyIds(CompoundTag tag) {
        if (!tag.hasUUID("bodyId1") || !tag.hasUUID("bodyId2")) {
            return null;
        }
        UUID bodyId1 = tag.getUUID("bodyId1");
        UUID bodyId2 = tag.getUUID("bodyId2");

        if (WORLD_BODY_ID.equals(bodyId2)) {
            return new UUID[]{bodyId1, null};
        }
        return new UUID[]{bodyId1, bodyId2};
    }
}