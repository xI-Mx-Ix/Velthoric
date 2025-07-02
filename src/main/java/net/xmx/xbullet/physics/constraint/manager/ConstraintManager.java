package net.xmx.xbullet.physics.constraint.manager;

import com.github.stephengold.joltjni.RVec3;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.xmx.xbullet.physics.XBulletSavedData;
import net.xmx.xbullet.physics.constraint.IConstraint;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.PhysicsObjectManager;
import net.xmx.xbullet.physics.physicsworld.PhysicsWorld;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class ConstraintManager {

    private final Map<UUID, IConstraint> managedConstraints = new ConcurrentHashMap<>();
    @Nullable private PhysicsWorld physicsWorld;
    @Nullable private ServerLevel managedLevel;
    @Nullable private XBulletSavedData savedData;
    private final ConstraintLoader constraintLoader;
    private final PhysicsObjectManager objectManager;

    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private final AtomicBoolean isShutdown = new AtomicBoolean(false);

    public ConstraintManager(PhysicsObjectManager objectManager) {
        this.objectManager = objectManager;
        this.constraintLoader = new ConstraintLoader(this, objectManager);
    }

    public void initialize(PhysicsWorld physicsWorld) {
        if (isInitialized.get() || isShutdown.get()) return;

        this.physicsWorld = physicsWorld;
        this.managedLevel = physicsWorld.getLevel();

        if (this.physicsWorld == null || this.managedLevel == null) {
            System.err.println("ConstraintManager could not initialize: PhysicsWorld or Level is null.");
            return;
        }
        this.savedData = XBulletSavedData.get(managedLevel);
        this.isShutdown.set(false);
        this.isInitialized.set(true);
    }

    public void addManagedConstraint(IConstraint constraint) {
        if (!isInitialized() || constraint == null || physicsWorld == null || savedData == null) {
            return;
        }

        if (managedConstraints.putIfAbsent(constraint.getJointId(), constraint) == null) {
            physicsWorld.getPhysicsSystem().addConstraint(constraint.getJoltConstraint());
            CompoundTag tag = new CompoundTag();
            constraint.save(tag);
            savedData.updateJointData(constraint.getJointId(), tag);
        }
    }

    public void removeConstraint(UUID jointId, boolean permanent) {
        if (!isInitialized() || physicsWorld == null) return;

        IConstraint constraint = managedConstraints.remove(jointId);
        if (constraint != null) {
            physicsWorld.getPhysicsSystem().removeConstraint(constraint.getJoltConstraint());
            constraint.release();
            if (permanent && savedData != null) {
                savedData.removeJointData(jointId);
            }
        }
    }

    public void removeConstraintsForObject(UUID objectId) {
        if (!isInitialized()) return;

        List<UUID> idsToRemove = managedConstraints.values().stream()
                .filter(c -> c.getBody1Id().equals(objectId) || c.getBody2Id().equals(objectId))
                .map(IConstraint::getJointId)
                .collect(Collectors.toList());

        idsToRemove.forEach(id -> removeConstraint(id, false));
    }

    public void loadConstraintsForChunk(ChunkPos pos) {
        if (isInitialized()) {
            constraintLoader.loadConstraintsForChunk(pos);
        }
    }

    public void unloadConstraintsForChunk(ChunkPos pos) {
        if (!isInitialized()) {
            return;
        }

        List<UUID> idsToUnload = new ArrayList<>();

        for (IConstraint constraint : managedConstraints.values()) {
            boolean shouldUnload = false;

            if (isObjectInChunk(constraint.getBody1Id(), pos)) {
                shouldUnload = true;
            }

            if (!shouldUnload && constraint.getBody2Id() != null) {
                if (isObjectInChunk(constraint.getBody2Id(), pos)) {
                    shouldUnload = true;
                }
            }

            if (shouldUnload) {
                idsToUnload.add(constraint.getJointId());
            }
        }

        idsToUnload.forEach(id -> removeConstraint(id, false));
    }

    private boolean isObjectInChunk(UUID objectId, ChunkPos chunkPos) {
        if (objectId == null) {
            return false;
        }

        return objectManager.getObject(objectId)
                .map(obj -> {
                    RVec3 pos = obj.getCurrentTransform().getTranslation();
                    int cx = (int) Math.floor(pos.xx() / 16.0);
                    int cz = (int) Math.floor(pos.zz() / 16.0);
                    return cx == chunkPos.x && cz == chunkPos.z;
                })
                .orElse(false);
    }

    public CompletableFuture<IConstraint> getOrLoadConstraint(UUID jointId) {
        if (managedConstraints.containsKey(jointId)) {
            return CompletableFuture.completedFuture(managedConstraints.get(jointId));
        }

        CompletableFuture<IConstraint> pendingFuture = constraintLoader.getPendingLoad(jointId);
        if (pendingFuture != null) {
            return pendingFuture;
        }

        return constraintLoader.scheduleJointLoad(jointId);
    }

    public void shutdown() {
        if (isShutdown.getAndSet(true)) return;
        isInitialized.set(false);

        if (savedData != null) {
            for (IConstraint constraint : managedConstraints.values()) {
                CompoundTag tag = new CompoundTag();
                constraint.save(tag);
                savedData.updateJointData(constraint.getJointId(), tag);
            }
            savedData.setDirty();
        }

        constraintLoader.shutdown();

        managedConstraints.values().forEach(c -> {
            if (physicsWorld != null) {
                physicsWorld.getPhysicsSystem().removeConstraint(c.getJoltConstraint());
            }
            c.release();
        });
        managedConstraints.clear();

        this.physicsWorld = null;
        this.managedLevel = null;
        this.savedData = null;
    }

    public PhysicsObjectManager getObjectManager() { return objectManager; }
    public boolean isInitialized() { return isInitialized.get(); }
    public ServerLevel getManagedLevel() { return managedLevel; }
    public XBulletSavedData getSavedData() { return savedData; }
    public boolean isJointLoaded(UUID id) { return managedConstraints.containsKey(id); }
}