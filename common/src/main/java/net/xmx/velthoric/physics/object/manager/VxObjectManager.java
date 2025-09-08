package net.xmx.velthoric.physics.object.manager;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.physics.object.VxObjectType;
import net.xmx.velthoric.physics.object.VxAbstractBody;
import net.xmx.velthoric.physics.object.persistence.VxObjectStorage;
import net.xmx.velthoric.physics.object.type.VxRigidBody;
import net.xmx.velthoric.physics.object.type.VxSoftBody;
import net.xmx.velthoric.physics.terrain.VxSectionPos;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class VxObjectManager {

    private final VxPhysicsWorld world;
    private final VxObjectStorage objectStorage;
    private final VxObjectContainer objectContainer;
    private final VxPhysicsUpdater physicsUpdater;
    private final VxObjectNetworkDispatcher networkDispatcher;

    private final ConcurrentHashMap<UUID, VxAbstractBody> pendingActivations = new ConcurrentHashMap<>();
    private final List<UUID> uuidsToRemove = new ArrayList<>();
    private final List<Integer> bodyIdsToActivate = new ArrayList<>();

    public VxObjectManager(VxPhysicsWorld world) {
        this.world = world;
        this.objectContainer = new VxObjectContainer(world);
        this.objectStorage = new VxObjectStorage(world.getLevel(), this);
        this.physicsUpdater = new VxPhysicsUpdater(this);
        this.networkDispatcher = new VxObjectNetworkDispatcher(world.getLevel(), this);
    }

    public void initialize() {
        objectStorage.initialize();
        networkDispatcher.start();
    }

    public void shutdown() {
        networkDispatcher.stop();
        objectContainer.getAllObjects().forEach(objectStorage::storeObject);
        pendingActivations.values().forEach(objectStorage::storeObject);
        objectStorage.saveDirtyRegions();
        objectContainer.clear();
        pendingActivations.clear();
        objectStorage.shutdown();
    }

    public void onPhysicsUpdate(long timestampNanos) {
        tickPendingActivations();
        physicsUpdater.update(timestampNanos, this.world);
    }

    void addRigidBodyToPhysicsWorld(VxRigidBody body, EActivation activation, boolean usePendingActivation) {
        try (ShapeSettings shapeSettings = body.createShapeSettings()) {
            if (shapeSettings == null)
                throw new IllegalStateException("createShapeSettings returned null for type: " + body.getType().getTypeId());
            try (ShapeResult shapeResult = shapeSettings.create()) {
                if (shapeResult.hasError())
                    throw new IllegalStateException("Shape creation failed: " + shapeResult.getError());
                try (ShapeRefC shapeRef = shapeResult.get()) {
                    try (BodyCreationSettings bcs = body.createBodyCreationSettings(shapeRef)) {
                        bcs.setPosition(body.getGameTransform().getTranslation());
                        bcs.setRotation(body.getGameTransform().getRotation());

                        int bodyId = world.getBodyInterface().createAndAddBody(bcs, activation);
                        if (bodyId == Jolt.cInvalidBodyId) {
                            VxMainClass.LOGGER.error("Jolt failed to create/add rigid body for {}", body.getPhysicsId());
                            return;
                        }
                        body.setBodyId(bodyId);
                        objectContainer.add(body);
                        body.onBodyAdded(world);

                        if (activation == EActivation.DontActivate && usePendingActivation) {
                            pendingActivations.put(body.getPhysicsId(), body);
                        }
                        world.getConstraintManager().getDataSystem().onDependencyLoaded(body.getPhysicsId());
                    }
                }
            }
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Failed to create/add rigid body {}", body.getPhysicsId(), e);
        }
    }

    void addSoftBodyToPhysicsWorld(VxSoftBody body, EActivation activation, boolean usePendingActivation) {
        try (SoftBodySharedSettings sharedSettings = body.createSoftBodySharedSettings()) {
            if (sharedSettings == null)
                throw new IllegalStateException("createSoftBodySharedSettings returned null for type: " + body.getType().getTypeId());
            try (SoftBodyCreationSettings settings = body.createSoftBodyCreationSettings(sharedSettings)) {
                settings.setPosition(body.getGameTransform().getTranslation());
                settings.setRotation(body.getGameTransform().getRotation());

                int bodyId = world.getBodyInterface().createAndAddSoftBody(settings, activation);
                if (bodyId == Jolt.cInvalidBodyId) {
                    VxMainClass.LOGGER.error("Jolt failed to create/add soft body for {}", body.getPhysicsId());
                    return;
                }
                body.setBodyId(bodyId);
                objectContainer.add(body);
                body.onBodyAdded(world);

                if (activation == EActivation.DontActivate && usePendingActivation) {
                    pendingActivations.put(body.getPhysicsId(), body);
                }
                world.getConstraintManager().getDataSystem().onDependencyLoaded(body.getPhysicsId());
            }
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Failed to create/add soft body {}", body.getPhysicsId(), e);
        }
    }

    public void addConstructedBody(VxAbstractBody body) {
        world.execute(() -> {
            if (body instanceof VxRigidBody rigidBody) {
                addRigidBodyToPhysicsWorld(rigidBody, EActivation.DontActivate, true);
            } else if (body instanceof VxSoftBody softBody) {
                addSoftBodyToPhysicsWorld(softBody, EActivation.DontActivate, true);
            }
        });
    }

    public void reAddObjectToWorld(VxAbstractBody body) {
        world.execute(() -> {
            if (body instanceof VxRigidBody rigidBody) {
                addRigidBodyToPhysicsWorld(rigidBody, EActivation.DontActivate, false);
            } else if (body instanceof VxSoftBody softBody) {
                addSoftBodyToPhysicsWorld(softBody, EActivation.DontActivate, false);
            }
        });
    }

    public <T extends VxRigidBody> Optional<T> createRigidBody(VxObjectType<T> type, VxTransform transform, Consumer<T> configurator) {
        T body = type.create(world, UUID.randomUUID());
        body.getGameTransform().set(transform);
        configurator.accept(body);
        world.execute(() ->
                addRigidBodyToPhysicsWorld(body, EActivation.DontActivate, true)
        );
        return Optional.of(body);
    }

    public <T extends VxSoftBody> Optional<T> createSoftBody(VxObjectType<T> type, VxTransform transform, Consumer<T> configurator) {
        T body = type.create(world, UUID.randomUUID());
        body.getGameTransform().set(transform);
        configurator.accept(body);
        world.execute(() ->
                addSoftBodyToPhysicsWorld(body, EActivation.DontActivate, true)
        );
        return Optional.of(body);
    }

    private void tickPendingActivations() {
        if (pendingActivations.isEmpty()) {
            return;
        }

        final BodyInterface bodyInterface = world.getBodyInterface();
        if (bodyInterface == null) {
            return;
        }

        uuidsToRemove.clear();
        bodyIdsToActivate.clear();

        for (var entry : pendingActivations.entrySet()) {
            UUID id = entry.getKey();
            VxAbstractBody body = entry.getValue();

            if (body == null || body.getBodyId() == 0 || body.getBodyId() == Jolt.cInvalidBodyId) {
                VxMainClass.LOGGER.warn("Pending body {} is invalid. Removing.", id);
                uuidsToRemove.add(id);
                continue;
            }

            if (bodyInterface.isActive(body.getBodyId())) {
                uuidsToRemove.add(id);
                continue;
            }

            var pos = body.getGameTransform().getTranslation();

            VxSectionPos sectionPos = VxSectionPos.fromWorldSpace(pos.xx(), pos.yy(), pos.zz());
            SectionPos minecraftSectionPos = SectionPos.of(sectionPos.x(), sectionPos.y(), sectionPos.z());

            if (world.getTerrainSystem().isSectionReady(minecraftSectionPos)) {
                bodyIdsToActivate.add(body.getBodyId());
                uuidsToRemove.add(id);
            }
        }

        if (!bodyIdsToActivate.isEmpty()) {
            world.execute(() -> {
                for (int bodyId : bodyIdsToActivate) {
                    try {

                        if (bodyInterface.isAdded(bodyId)) {
                            bodyInterface.activateBody(bodyId);
                        }
                    } catch (Exception e) {
                        VxMainClass.LOGGER.error("Failed to activate body {}", bodyId, e);
                    }
                }
            });
        }

        if (!uuidsToRemove.isEmpty()) {
            for (UUID id : uuidsToRemove) {
                pendingActivations.remove(id);
            }
        }
    }

    public void removeObject(UUID id, VxRemovalReason reason) {
        final VxAbstractBody obj = objectContainer.remove(id);
        if (obj == null) {
            VxMainClass.LOGGER.warn("Attempted to remove non-existent body: {}", id);
            if (reason == VxRemovalReason.DISCARD) {
                objectStorage.removeData(id);
            }
            world.getConstraintManager().removeConstraintsForObject(id, reason == VxRemovalReason.DISCARD);
            return;
        }
        obj.onBodyRemoved(world, reason);
        physicsUpdater.clearStateFor(id);

        if (reason == VxRemovalReason.SAVE) {
            obj.getTransform(world).ifPresent(t -> obj.getGameTransform().set(t));
            objectStorage.storeObject(obj);
        } else if (reason == VxRemovalReason.DISCARD) {
            objectStorage.removeData(id);
        }

        world.getConstraintManager().removeConstraintsForObject(id, reason == VxRemovalReason.DISCARD);

        final int bodyIdToRemove = obj.getBodyId();

        pendingActivations.remove(id);

        if (bodyIdToRemove != 0 && bodyIdToRemove != Jolt.cInvalidBodyId) {
            world.execute(() -> {
                if (world.getBodyInterface().isAdded(bodyIdToRemove)) {
                    world.getBodyInterface().removeBody(bodyIdToRemove);
                }
                world.getBodyInterface().destroyBody(bodyIdToRemove);
            });
        }
    }

    public Optional<VxAbstractBody> getObject(UUID id) {
        return objectContainer.get(id);
    }

    public CompletableFuture<VxAbstractBody> getOrLoadObject(UUID id) {
        if (id == null) {
            return CompletableFuture.completedFuture(null);
        }
        Optional<VxAbstractBody> loadedObject = getObject(id);
        if (loadedObject.isPresent()) {
            return CompletableFuture.completedFuture(loadedObject.get());
        }
        return objectStorage.loadObject(id);
    }

    public VxPhysicsWorld getPhysicsWorld() {
        return world;
    }

    public VxObjectContainer getObjectContainer() {
        return objectContainer;
    }

    public VxObjectStorage getObjectStorage() {
        return objectStorage;
    }

    public VxObjectNetworkDispatcher getNetworkDispatcher() {
        return networkDispatcher;
    }

    public static ChunkPos getObjectChunkPos(VxAbstractBody body) {
        var pos = body.getGameTransform().getTranslation();
        return new ChunkPos(SectionPos.posToSectionCoord(pos.xx()), SectionPos.posToSectionCoord(pos.zz()));
    }
}