package net.xmx.xbullet.physics.constraint.manager;

import com.github.stephengold.joltjni.RVec3;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.xmx.xbullet.physics.XBulletSavedData;
import net.xmx.xbullet.physics.constraint.IConstraint;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.ObjectManager;
import net.xmx.xbullet.physics.world.PhysicsWorld;

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
    private final ObjectManager objectManager;

    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private final AtomicBoolean isShutdown = new AtomicBoolean(false);

    public ConstraintManager(ObjectManager objectManager) {
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

            physicsWorld.execute(() -> {
                if (physicsWorld.getPhysicsSystem() != null) {
                    physicsWorld.getPhysicsSystem().addConstraint(constraint.getJoltConstraint());
                }
            });

            CompoundTag tag = new CompoundTag();
            constraint.save(tag);

            managedLevel.getServer().execute(() -> {
                savedData.updateJointData(constraint.getJointId(), tag);
                savedData.setDirty();
            });
        }
    }

    public void removeConstraint(UUID jointId, boolean permanent) {
        if (!isInitialized() || physicsWorld == null) return;

        IConstraint constraint = managedConstraints.remove(jointId);

        if (constraint != null) {

            physicsWorld.execute(() -> {
                physicsWorld.getPhysicsSystem().removeConstraint(constraint.getJoltConstraint());
                constraint.release();
            });

            managedLevel.getServer().execute(() -> {
                if (!permanent && savedData != null) {
                    CompoundTag tag = new CompoundTag();
                    constraint.save(tag);
                    savedData.updateJointData(constraint.getJointId(), tag);
                    savedData.setDirty();
                } else if (permanent && savedData != null) {
                    savedData.removeJointData(jointId);
                    savedData.setDirty();
                }
            });
        }
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
            boolean body1InChunk = objectManager.getObject(constraint.getBody1Id())
                    .map(obj -> isObjectInChunk(obj.getCurrentTransform().getTranslation(), pos))
                    .orElse(false);

            boolean body2InChunk = (constraint.getBody2Id() != null) && objectManager.getObject(constraint.getBody2Id())
                    .map(obj -> isObjectInChunk(obj.getCurrentTransform().getTranslation(), pos))
                    .orElse(false);

            if (body1InChunk || body2InChunk) {
                idsToUnload.add(constraint.getJointId());
            }
        }

        idsToUnload.forEach(id -> removeConstraint(id, false));
    }

    private boolean isObjectInChunk(RVec3 position, ChunkPos chunkPos) {
        int cx = (int) Math.floor(position.xx() / 16.0);
        int cz = (int) Math.floor(position.zz() / 16.0);
        return cx == chunkPos.x && cz == chunkPos.z;
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

        List<IConstraint> constraintsToShutdown = new ArrayList<>(managedConstraints.values());

        managedConstraints.clear();

        if (physicsWorld != null) {

            physicsWorld.execute(() -> {
                for (IConstraint c : constraintsToShutdown) {
                    if (physicsWorld.getPhysicsSystem() != null) {
                        physicsWorld.getPhysicsSystem().removeConstraint(c.getJoltConstraint());
                    }
                    c.release();
                }
            });
        }

        this.physicsWorld = null;
        this.managedLevel = null;
        this.savedData = null;
    }

    public void removeConstraintsForObject(UUID objectId, boolean permanent) {
        if (!isInitialized()) return;

        List<UUID> idsToRemove = managedConstraints.values().stream()
                .filter(c -> {

                    boolean isBody1 = c.getBody1Id().equals(objectId);
                    boolean isBody2 = c.getBody2Id() != null && c.getBody2Id().equals(objectId);
                    return isBody1 || isBody2;
                })
                .map(IConstraint::getJointId)
                .collect(Collectors.toList());

        idsToRemove.forEach(id -> removeConstraint(id, permanent));
    }

    public ObjectManager getObjectManager() { return objectManager; }
    public boolean isInitialized() { return isInitialized.get(); }
    public ServerLevel getManagedLevel() { return managedLevel; }
    public XBulletSavedData getSavedData() { return savedData; }
    public boolean isJointLoaded(UUID id) { return managedConstraints.containsKey(id); }
}