package net.xmx.xbullet.physics.constraint.manager;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.physics.constraint.IConstraint;
import net.xmx.xbullet.physics.constraint.persistence.ConstraintStorage;
import net.xmx.xbullet.physics.constraint.serializer.base.ConstraintSerializer;
import net.xmx.xbullet.physics.constraint.serializer.registry.ConstraintSerializerRegistry;
import net.xmx.xbullet.physics.object.physicsobject.IPhysicsObject;
import net.xmx.xbullet.physics.object.physicsobject.manager.ObjectManager;
import net.xmx.xbullet.physics.world.PhysicsWorld;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConstraintDataSystem {

    private final ConstraintManager constraintManager;
    private final ObjectManager objectManager;
    private ConstraintStorage storage;
    private ExecutorService loadingExecutor;
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private final PhysicsWorld physicsWorld;

    public ConstraintDataSystem(ConstraintManager manager) {
        this.constraintManager = manager;
        this.objectManager = manager.getObjectManager();
        this.physicsWorld = objectManager.getPhysicsWorld();
    }

    public void initialize(ServerLevel level) {
        if (isInitialized.getAndSet(true)) return;

        int threadCount = Math.max(1, Runtime.getRuntime().availableProcessors() / 4);
        this.loadingExecutor = Executors.newFixedThreadPool(threadCount, r -> new Thread(r, "XBullet-ConstraintLoader-Pool"));
        this.storage = new ConstraintStorage(level);
        this.storage.loadFromFile();
    }

    public void checkForConstraintsForObject(UUID loadedObjectId) {
        if (!isInitialized.get() || loadedObjectId == null || physicsWorld == null) return;

        Map<UUID, byte[]> dataToCheck = storage.getUnloadedConstraintsData();
        if (dataToCheck.isEmpty()) {
            return;
        }

        List<UUID> constraintsToAttemptLoading = new ArrayList<>();
        for (Map.Entry<UUID, byte[]> entry : new HashMap<>(dataToCheck).entrySet()) {
            UUID constraintId = entry.getKey();
            byte[] data = entry.getValue();
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));

            try {
                buf.markReaderIndex();
                String typeId = buf.readUtf();
                Optional<ConstraintSerializer<?, ?, ?>> serializerOpt = ConstraintSerializerRegistry.getSerializer(typeId);
                if (serializerOpt.isEmpty()) continue;
                ConstraintSerializer<?, ?, ?> serializer = serializerOpt.get();

                if ("xbullet:rack_and_pinion".equals(typeId) || "xbullet:gear".equals(typeId)) continue;

                UUID[] bodyIds = serializer.deserializeBodies(buf);
                UUID body1Id = bodyIds[0];
                UUID body2Id = bodyIds[1];

                boolean isRelevant = loadedObjectId.equals(body1Id) || loadedObjectId.equals(body2Id);
                if (isRelevant) {
                    UUID otherBodyId = loadedObjectId.equals(body1Id) ? body2Id : body1Id;

                    boolean otherBodyIsReady = (otherBodyId == null)
                            || objectManager.getObject(otherBodyId).map(obj -> obj.getBodyId() != 0).orElse(false);

                    if (otherBodyIsReady) {
                        constraintsToAttemptLoading.add(constraintId);
                    }
                }
            } catch (Exception e) {
                XBullet.LOGGER.error("Failed to peek into constraint data for relevance check, ID: {}", constraintId, e);
            } finally {
                buf.release();
            }
        }

        for (UUID constraintId : constraintsToAttemptLoading) {
            byte[] data = storage.takeConstraintData(constraintId);
            if (data != null) {
                FriendlyByteBuf finalBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
                UUID finalB1Id = null, finalB2Id = null, finalD1Id = null, finalD2Id = null;
                try {
                    String typeId = finalBuf.readUtf();
                    ConstraintSerializer<?, ?, ?> s = ConstraintSerializerRegistry.getSerializer(typeId).orElseThrow();
                    if ("xbullet:rack_and_pinion".equals(typeId) || "xbullet:gear".equals(typeId)) {
                        finalD1Id = finalBuf.readUUID();
                        finalD2Id = finalBuf.readUUID();
                    } else {
                        UUID[] ids = s.deserializeBodies(finalBuf);
                        finalB1Id = ids[0];
                        finalB2Id = ids[1];
                    }
                } catch(Exception e) {
                    XBullet.LOGGER.error("Could not read body IDs for final constraint creation call for {}", constraintId, e);
                } finally {
                    finalBuf.release();
                }

                final UUID b1 = finalB1Id;
                final UUID b2 = finalB2Id;
                final UUID d1 = finalD1Id;
                final UUID d2 = finalD2Id;

                physicsWorld.execute(() -> {
                    constraintManager.createAndFinalizeConstraint(constraintId, data, b1, b2, d1, d2);
                });
            }
        }
    }


    public void unloadConstraintsInChunk(ChunkPos chunkPos) {
        if (!isInitialized.get()) return;

        List<UUID> toUnload = new ArrayList<>();
        for (IConstraint constraint : constraintManager.getManagedConstraints()) {
            boolean shouldUnload = true;

            UUID body1Id = constraint.getBody1Id();
            if (body1Id != null && objectManager.isObjectInLoadedChunk(body1Id)) {
                shouldUnload = false;
            }

            UUID body2Id = constraint.getBody2Id();
            if (body2Id != null && objectManager.isObjectInLoadedChunk(body2Id)) {
                shouldUnload = false;
            }

            if (constraint.getConstraintType().equals("xbullet:gear") || constraint.getConstraintType().equals("xbullet:rack_and_pinion")) {
                UUID dep1Id = constraint.getDependency(0);
                UUID dep2Id = constraint.getDependency(1);
                if ((dep1Id != null && constraintManager.isJointLoaded(dep1Id)) || (dep2Id != null && constraintManager.isJointLoaded(dep2Id))) {
                    shouldUnload = false;
                }
            }

            if (shouldUnload) {
                toUnload.add(constraint.getId());
            }
        }

        if (!toUnload.isEmpty() && physicsWorld != null) {
            physicsWorld.execute(() -> toUnload.forEach(constraintManager::unloadConstraint));
        }
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

    public ConstraintStorage getStorage() {
        return storage;
    }

    public void shutdown() {
        if (!isInitialized.getAndSet(false)) return;
        saveAll(constraintManager.getManagedConstraints());
        if (loadingExecutor != null) {
            loadingExecutor.shutdown();
        }
        storage.clearData();
    }
}