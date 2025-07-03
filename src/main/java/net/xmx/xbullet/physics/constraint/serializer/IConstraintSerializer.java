package net.xmx.xbullet.physics.constraint.serializer;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.TwoBodyConstraint;
import net.minecraft.nbt.CompoundTag;
import net.xmx.xbullet.physics.constraint.manager.ConstraintManager;
import net.xmx.xbullet.physics.object.global.physicsobject.IPhysicsObject;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.PhysicsObjectManager;
import net.xmx.xbullet.physics.world.PhysicsWorld;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface IConstraintSerializer<T extends TwoBodyConstraint> {

    UUID WORLD_BODY_ID = new UUID(0L, 0L);

    void save(T constraint, CompoundTag tag);

    CompletableFuture<TwoBodyConstraint> createAndLink(CompoundTag tag, ConstraintManager constraintManager, PhysicsObjectManager objectManager);

    @FunctionalInterface
    interface ConstraintCreator<U extends TwoBodyConstraint> {
        U create(BodyInterface bodyInterface, int bodyId1, int bodyId2, CompoundTag tag);
    }

    default CompletableFuture<TwoBodyConstraint> createFromLoadedBodies(
            CompoundTag tag,
            PhysicsObjectManager objectManager,
            ConstraintCreator<T> creator) {

        UUID[] bodyIds = loadBodyIds(tag);
        if (bodyIds == null || bodyIds[0] == null) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<IPhysicsObject> future1 = objectManager.getOrLoadObject(bodyIds[0]);
        CompletableFuture<IPhysicsObject> future2 = (bodyIds[1] != null)
                ? objectManager.getOrLoadObject(bodyIds[1])
                : CompletableFuture.completedFuture(null);

        PhysicsWorld physicsWorld = objectManager.getPhysicsWorld();
        if (physicsWorld == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("PhysicsWorld is not available"));
        }

        return CompletableFuture.allOf(future1, future2).thenApplyAsync(v -> {
            IPhysicsObject obj1 = future1.join();
            IPhysicsObject obj2 = future2.join();

            if (obj1 == null) {
                return null;
            }

            int b1Id = obj1.getBodyId();
            int b2Id = (obj2 != null) ? obj2.getBodyId() : Body.sFixedToWorld().getId();

            if (b1Id == 0) {
                return null;
            }

            BodyInterface bodyInterface = physicsWorld.getBodyInterface();
            if (bodyInterface == null) {
                return null;
            }

            return creator.create(bodyInterface, b1Id, b2Id, tag);
        }, physicsWorld);
    }

    default void saveBodyIds(UUID bodyId1, @Nullable UUID bodyId2, CompoundTag tag) {
        tag.putUUID("bodyId1", bodyId1);
        tag.putUUID("bodyId2", bodyId2 != null ? bodyId2 : WORLD_BODY_ID);
    }

    @Nullable
    default UUID[] loadBodyIds(CompoundTag tag) {
        if (!tag.hasUUID("bodyId1")) {
            return null;
        }
        UUID bodyId1 = tag.getUUID("bodyId1");
        UUID bodyId2 = tag.hasUUID("bodyId2") ? tag.getUUID("bodyId2") : null;

        if (bodyId2 != null && WORLD_BODY_ID.equals(bodyId2)) {
            bodyId2 = null;
        }
        return new UUID[]{bodyId1, bodyId2};
    }
}