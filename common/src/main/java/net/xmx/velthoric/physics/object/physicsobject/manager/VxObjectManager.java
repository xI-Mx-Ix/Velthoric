package net.xmx.velthoric.physics.object.physicsobject.manager;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.xmx.velthoric.api.VelthoricAPI;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.physics.object.physicsobject.PhysicsObjectType;
import net.xmx.velthoric.physics.object.physicsobject.VxAbstractBody;
import net.xmx.velthoric.physics.object.physicsobject.manager.persistence.VxObjectStorage;
import net.xmx.velthoric.physics.object.physicsobject.manager.registry.VxObjectRegistry;
import net.xmx.velthoric.physics.object.physicsobject.type.rigid.VxRigidBody;
import net.xmx.velthoric.physics.object.physicsobject.type.soft.VxSoftBody;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class VxObjectManager {

    private final VxPhysicsWorld world;
    private final VxObjectRegistry objectRegistry;
    private final VxObjectStorage objectStorage;
    private final VxObjectContainer objectContainer;
    private final VxPhysicsUpdater physicsUpdater;
    private final VxObjectNetworkDispatcher networkDispatcher;

    public VxObjectManager(VxPhysicsWorld world) {
        this.world = world;
        this.objectRegistry = new VxObjectRegistry();
        this.objectContainer = new VxObjectContainer(world);
        this.objectStorage = new VxObjectStorage(world.getLevel(), this);
        this.physicsUpdater = new VxPhysicsUpdater(this);
        this.networkDispatcher = new VxObjectNetworkDispatcher(world.getLevel(), this);
    }

    public void initialize() {
        objectStorage.initialize();
        networkDispatcher.start();
        VelthoricAPI.getInstance().getQueuedRegistrations().values().forEach(this.objectRegistry::register);
    }

    public void shutdown() {
        networkDispatcher.stop();
        objectContainer.getAllObjects().forEach(objectStorage::storeObject);
        objectStorage.saveToFile();
        objectContainer.clear();
        objectStorage.shutdown();
    }

    public void onPhysicsUpdate(long timestampNanos) {
        physicsUpdater.update(timestampNanos, this.world);
    }

    private void addRigidBodyToPhysicsWorld(VxRigidBody body) {
        try (ShapeSettings shapeSettings = body.createShapeSettings()) {
            if (shapeSettings == null) throw new IllegalStateException("createShapeSettings returned null for type: " + body.getType().getTypeId());
            try (ShapeResult shapeResult = shapeSettings.create()) {
                if (shapeResult.hasError()) throw new IllegalStateException("Shape creation failed: " + shapeResult.getError());
                try (ShapeRefC shapeRef = shapeResult.get()) {
                    try (BodyCreationSettings bcs = body.createBodyCreationSettings(shapeRef)) {
                        bcs.setPosition(body.getGameTransform().getTranslation());
                        bcs.setRotation(body.getGameTransform().getRotation());
                        int bodyId = world.getBodyInterface().createAndAddBody(bcs, EActivation.Activate);
                        if (bodyId == Jolt.cInvalidBodyId) {
                            VxMainClass.LOGGER.error("Jolt failed to create/re-create rigid body for {}", body.getPhysicsId());
                            return;
                        }
                        body.setBodyId(bodyId);
                        objectContainer.add(body);
                        world.getConstraintManager().getDataSystem().onDependencyLoaded(body.getPhysicsId());
                    }
                }
            }
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Failed to create/re-create rigid body {}", body.getPhysicsId(), e);
        }
    }

    private void addSoftBodyToPhysicsWorld(VxSoftBody body) {
        try (SoftBodySharedSettings sharedSettings = body.createSoftBodySharedSettings()) {
            if (sharedSettings == null) throw new IllegalStateException("createSoftBodySharedSettings returned null for type: " + body.getType().getTypeId());
            try (SoftBodyCreationSettings settings = body.createSoftBodyCreationSettings(sharedSettings)) {
                settings.setPosition(body.getGameTransform().getTranslation());
                settings.setRotation(body.getGameTransform().getRotation());
                int bodyId = world.getBodyInterface().createAndAddSoftBody(settings, EActivation.Activate);
                if (bodyId == Jolt.cInvalidBodyId) {
                    VxMainClass.LOGGER.error("Jolt failed to create/re-create soft body for {}", body.getPhysicsId());
                    return;
                }
                body.setBodyId(bodyId);
                objectContainer.add(body);
                world.getConstraintManager().getDataSystem().onDependencyLoaded(body.getPhysicsId());
            }
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Failed to create/re-create soft body {}", body.getPhysicsId(), e);
        }
    }

    public void reAddObjectToWorld(VxAbstractBody body) {
        world.execute(() -> {
            if (body instanceof VxRigidBody rigidBody) {
                addRigidBodyToPhysicsWorld(rigidBody);
            } else if (body instanceof VxSoftBody softBody) {
                addSoftBodyToPhysicsWorld(softBody);
            }
        });
    }

    public <T extends VxRigidBody> Optional<T> createRigidBody(PhysicsObjectType<T> type, VxTransform transform, Consumer<T> configurator) {
        T body = type.create(world, UUID.randomUUID());
        body.getGameTransform().set(transform);
        configurator.accept(body);
        world.execute(() -> addRigidBodyToPhysicsWorld(body));
        return Optional.of(body);
    }

    public <T extends VxSoftBody> Optional<T> createSoftBody(PhysicsObjectType<T> type, VxTransform transform, Consumer<T> configurator) {
        T body = type.create(world, UUID.randomUUID());
        body.getGameTransform().set(transform);
        configurator.accept(body);
        world.execute(() -> addSoftBodyToPhysicsWorld(body));
        return Optional.of(body);
    }

    public void removeObject(UUID id, VxRemovalReason reason) {
        final VxAbstractBody obj = objectContainer.remove(id);
        if (obj == null) {
            if (reason == VxRemovalReason.DISCARD) {
                objectStorage.removeData(id);
            }
            world.getConstraintManager().removeConstraintsForObject(id, reason == VxRemovalReason.DISCARD);
            return;
        }
        physicsUpdater.clearStateFor(id);

        if (reason == VxRemovalReason.SAVE) {
            obj.getTransform(world).ifPresent(t -> obj.getGameTransform().set(t));
            objectStorage.storeObject(obj);
        } else if (reason == VxRemovalReason.DISCARD) {
            objectStorage.removeData(id);
        }

        world.getConstraintManager().removeConstraintsForObject(id, reason == VxRemovalReason.DISCARD);

        final int bodyIdToRemove = obj.getBodyId();
        if (bodyIdToRemove != 0) {
            world.execute(() -> {
                world.getBodyInterface().removeBody(bodyIdToRemove);
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

    public VxPhysicsWorld getWorld() {
        return world;
    }

    public VxObjectContainer getObjectContainer() {
        return objectContainer;
    }

    public VxObjectStorage getObjectStorage() {
        return objectStorage;
    }

    public VxObjectRegistry getObjectRegistry() {
        return objectRegistry;
    }

    public VxObjectNetworkDispatcher getNetworkDispatcher() {
        return networkDispatcher;
    }

    public static ChunkPos getObjectChunkPos(VxAbstractBody body) {
        var pos = body.getGameTransform().getTranslation();
        return new ChunkPos(SectionPos.posToSectionCoord(pos.xx()), SectionPos.posToSectionCoord(pos.zz()));
    }
}