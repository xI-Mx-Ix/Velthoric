package net.xmx.vortex.physics.constraint.manager;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;
import net.xmx.vortex.init.VxMainClass;
import net.xmx.vortex.physics.constraint.IConstraint;
import net.xmx.vortex.physics.constraint.persistence.ConstraintStorage;
import net.xmx.vortex.physics.constraint.serializer.base.ConstraintSerializer;
import net.xmx.vortex.physics.constraint.serializer.registry.ConstraintSerializerRegistry;
import net.xmx.vortex.physics.object.physicsobject.manager.VxObjectManager;
import net.xmx.vortex.physics.world.VxPhysicsWorld;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConstraintDataSystem {

    private final VxConstraintManager constraintManager;
    private final VxObjectManager objectManager;
    private VxPhysicsWorld physicsWorld;
    private ConstraintStorage storage;
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    private final Map<UUID, List<UUID>> dependencyToConstraintsMap = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> constraintToDependenciesMap = new ConcurrentHashMap<>();

    public ConstraintDataSystem(VxConstraintManager manager) {
        this.constraintManager = manager;
        this.objectManager = manager.getObjectManager();
    }

    public void initialize(VxPhysicsWorld world) {
        if (isInitialized.getAndSet(true)) return;
        this.physicsWorld = world;
        this.storage = new ConstraintStorage(world.getLevel());
        this.storage.loadFromFile();
        buildDependencyMaps();
    }

    private void buildDependencyMaps() {
        Map<UUID, byte[]> unloadedData = storage.getUnloadedConstraintsData();

        for (Map.Entry<UUID, byte[]> entry : unloadedData.entrySet()) {
            UUID constraintId = entry.getKey();
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(entry.getValue()));
            try {
                String typeId = buf.readUtf();
                ConstraintSerializer<?, ?, ?> serializer = ConstraintSerializerRegistry.getSerializer(typeId)
                        .orElseThrow(() -> new IllegalStateException("Missing serializer for " + typeId));

                List<UUID> dependencies = new ArrayList<>();
                if ("vortex:rack_and_pinion".equals(typeId) || "vortex:gear".equals(typeId)) {
                    dependencies.add(buf.readUUID());
                    dependencies.add(buf.readUUID());
                } else {
                    UUID[] bodyIds = serializer.deserializeBodies(buf);
                    if (bodyIds[0] != null) dependencies.add(bodyIds[0]);
                    if (bodyIds[1] != null) dependencies.add(bodyIds[1]);
                }

                if (!dependencies.isEmpty()) {
                    Set<UUID> dependencySet = new HashSet<>(dependencies);
                    constraintToDependenciesMap.put(constraintId, dependencySet);
                    for (UUID depId : dependencySet) {
                        dependencyToConstraintsMap.computeIfAbsent(depId, k -> new ArrayList<>()).add(constraintId);
                    }
                }
            } catch (Exception e) {
                VxMainClass.LOGGER.error("Failed to parse dependencies for unloaded constraint {}, skipping.", constraintId, e);
            } finally {
                buf.release();
            }
        }
    }

    public void onDependencyLoaded(UUID loadedDependencyId) {
        if (!isInitialized.get() || loadedDependencyId == null || this.physicsWorld == null) return;

        List<UUID> potentialConstraints = dependencyToConstraintsMap.get(loadedDependencyId);
        if (potentialConstraints == null || potentialConstraints.isEmpty()) {
            return;
        }

        List<UUID> constraintsToCheck = new ArrayList<>(potentialConstraints);

        for (UUID constraintId : constraintsToCheck) {
            Set<UUID> remainingDependencies = constraintToDependenciesMap.get(constraintId);
            if (remainingDependencies == null) {
                continue;
            }

            remainingDependencies.remove(loadedDependencyId);

            if (remainingDependencies.isEmpty()) {
                constraintToDependenciesMap.remove(constraintId);
                byte[] data = storage.takeConstraintData(constraintId);
                if (data != null) {
                    physicsWorld.execute(() -> constraintManager.createAndFinalizeConstraintFromData(constraintId, data));
                }
            }
        }
    }

    public void unloadConstraintsInChunk(ChunkPos chunkPos) {
        if (!isInitialized.get() || physicsWorld == null) return;

        Set<UUID> toUnload = new HashSet<>();
        for (IConstraint constraint : constraintManager.getManagedConstraints()) {
            boolean isAffected = false;

            UUID body1Id = constraint.getBody1Id();
            if (body1Id != null && objectManager.getObject(body1Id).map(obj -> VxObjectManager.getObjectChunkPos(obj).equals(chunkPos)).orElse(false)) {
                isAffected = true;
            }
            UUID body2Id = constraint.getBody2Id();
            if (body2Id != null && objectManager.getObject(body2Id).map(obj -> VxObjectManager.getObjectChunkPos(obj).equals(chunkPos)).orElse(false)) {
                isAffected = true;
            }

            if (isAffected) {
                boolean shouldUnload = true;
                if (body1Id != null && objectManager.getObjectContainer().hasObject(body1Id) && !objectManager.getObject(body1Id).map(obj -> VxObjectManager.getObjectChunkPos(obj).equals(chunkPos)).orElse(false)) {
                    shouldUnload = false;
                }
                if (body2Id != null && objectManager.getObjectContainer().hasObject(body2Id) && !objectManager.getObject(body2Id).map(obj -> VxObjectManager.getObjectChunkPos(obj).equals(chunkPos)).orElse(false)) {
                    shouldUnload = false;
                }

                if (constraint.getConstraintType().equals("vortex:gear") || constraint.getConstraintType().equals("vortex:rack_and_pinion")) {
                    UUID dep1Id = constraint.getDependency(0);
                    UUID dep2Id = constraint.getDependency(1);
                    if ((dep1Id != null && constraintManager.isJointLoaded(dep1Id)) || (dep2Id != null && constraintManager.isJointLoaded(dep2Id))) {
                        IConstraint dep1 = constraintManager.getConstraint(dep1Id);
                        IConstraint dep2 = constraintManager.getConstraint(dep2Id);
                        if ((dep1 != null && !isConstraintBodyInChunk(dep1, chunkPos)) || (dep2 != null && !isConstraintBodyInChunk(dep2, chunkPos))) {
                            shouldUnload = false;
                        }
                    }
                }

                if (shouldUnload) {
                    toUnload.add(constraint.getId());
                }
            }
        }

        if (!toUnload.isEmpty()) {
            physicsWorld.execute(() -> toUnload.forEach(id -> constraintManager.unloadConstraint(id, false)));
        }
    }

    private boolean isConstraintBodyInChunk(IConstraint c, ChunkPos pos) {
        return (c.getBody1Id() != null && objectManager.getObject(c.getBody1Id()).map(obj -> VxObjectManager.getObjectChunkPos(obj).equals(pos)).orElse(false)) ||
                (c.getBody2Id() != null && objectManager.getObject(c.getBody2Id()).map(obj -> VxObjectManager.getObjectChunkPos(obj).equals(pos)).orElse(false));
    }

    public void onConstraintUnloaded(UUID constraintId) {
    }

    public void saveAll(Collection<IConstraint> activeConstraints) {
        if (storage != null) storage.saveToFile(activeConstraints);
    }

    public void storeConstraint(IConstraint c) {
        if (storage != null) storage.storeConstraintData(c);
    }

    public void removePermanent(UUID id) {
        if (storage != null) storage.removeConstraintData(id);
    }

    public void shutdown() {
        if (!isInitialized.getAndSet(false)) return;
        saveAll(constraintManager.getManagedConstraints());

        dependencyToConstraintsMap.clear();
        constraintToDependenciesMap.clear();

        if (storage != null) {
            storage.clearData();
        }
        this.physicsWorld = null;
    }
}