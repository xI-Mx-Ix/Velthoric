package net.xmx.xbullet.physics.constraint.serializer;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.TwoBodyConstraint;
import net.minecraft.nbt.CompoundTag;
import net.xmx.xbullet.physics.constraint.manager.ConstraintManager;
import net.xmx.xbullet.physics.object.physicsobject.IPhysicsObject;
import net.xmx.xbullet.physics.object.physicsobject.manager.ObjectManager;
import net.xmx.xbullet.physics.world.PhysicsWorld;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface IConstraintSerializer<T extends TwoBodyConstraint> {

    UUID WORLD_BODY_ID = new UUID(0L, 0L);

    void save(T constraint, CompoundTag tag);

    CompletableFuture<TwoBodyConstraint> createAndLink(CompoundTag tag, ConstraintManager constraintManager, ObjectManager objectManager);

    @FunctionalInterface
    interface ConstraintCreator<U extends TwoBodyConstraint> {
        U create(BodyInterface bodyInterface, int bodyId1, int bodyId2, CompoundTag tag);
    }

    default CompletableFuture<TwoBodyConstraint> createFromLoadedBodies(
            CompoundTag tag,
            ObjectManager objectManager,
            ConstraintCreator<T> creator) {

        UUID[] bodyIds = loadBodyIds(tag);
        if (bodyIds == null) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<IPhysicsObject> future1 = (bodyIds[0] != null)
                ? objectManager.getOrLoadObject(bodyIds[0])
                : CompletableFuture.completedFuture(null);

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

            if (obj1 == null && obj2 == null) {
                return null;
            }

            int b1Id = (obj1 != null) ? obj1.getBodyId() : Body.sFixedToWorld().getId();
            int b2Id = (obj2 != null) ? obj2.getBodyId() : Body.sFixedToWorld().getId();

            if ((obj1 != null && b1Id == 0) || (obj2 != null && b2Id == 0)) {
                return null;
            }

            BodyInterface bodyInterface = physicsWorld.getBodyInterface();
            if (bodyInterface == null) {
                return null;
            }

            return creator.create(bodyInterface, b1Id, b2Id, tag);
        }, physicsWorld);
    }

    default void saveBodyIds(@Nullable UUID bodyId1, @Nullable UUID bodyId2, CompoundTag tag) {
        tag.putUUID("bodyId1", bodyId1 != null ? bodyId1 : WORLD_BODY_ID);
        tag.putUUID("bodyId2", bodyId2 != null ? bodyId2 : WORLD_BODY_ID);
    }

    @Nullable
    default UUID[] loadBodyIds(CompoundTag tag) {
        if (!tag.hasUUID("bodyId1") || !tag.hasUUID("bodyId2")) {
            return null;
        }

        UUID id1 = tag.getUUID("bodyId1");
        UUID id2 = tag.getUUID("bodyId2");

        UUID bodyId1 = WORLD_BODY_ID.equals(id1) ? null : id1;
        UUID bodyId2 = WORLD_BODY_ID.equals(id2) ? null : id2;

        return new UUID[]{bodyId1, bodyId2};
    }
}