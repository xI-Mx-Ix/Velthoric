package net.xmx.vortex.physics.object.physicsobject.manager;

import com.github.stephengold.joltjni.PhysicsSystem;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.xmx.vortex.api.VortexAPI;
import net.xmx.vortex.init.VxMainClass;
import net.xmx.vortex.math.VxTransform;
import net.xmx.vortex.physics.object.physicsobject.IPhysicsObject;
import net.xmx.vortex.physics.object.physicsobject.PhysicsObjectType;
import net.xmx.vortex.physics.object.physicsobject.manager.persistence.VxObjectStorage;
import net.xmx.vortex.physics.object.physicsobject.manager.registry.VxObjectRegistry;
import net.xmx.vortex.physics.object.riding.RidingManager;
import net.xmx.vortex.physics.world.VxPhysicsWorld;

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
    private final RidingManager ridingManager;

    public VxObjectManager(VxPhysicsWorld world) {
        this.world = world;
        ServerLevel level = world.getLevel();
        this.objectRegistry = new VxObjectRegistry();
        this.objectContainer = new VxObjectContainer(world);
        this.objectStorage = new VxObjectStorage(level, this);
        this.physicsUpdater = new VxPhysicsUpdater(this);
        this.networkDispatcher = new VxObjectNetworkDispatcher(level, this);
        this.ridingManager = new RidingManager(world);
    }

    public void initialize() {
        objectStorage.initialize();
        VortexAPI.getInstance().getQueuedRegistrations().values().forEach(this.objectRegistry::register);
    }

    public void shutdown() {
        objectStorage.saveAll(objectContainer.getAllObjects());
        objectContainer.clear();
        objectStorage.shutdown();
    }

    public void onPhysicsUpdate(long timestampNanos, PhysicsSystem physicsSystem) {
        physicsUpdater.update(timestampNanos, physicsSystem.getBodyLockInterface());
    }

    public <T extends IPhysicsObject> Optional<T> spawnObject(PhysicsObjectType<T> type, VxTransform transform, Consumer<T> configurator) {
        UUID id = UUID.randomUUID();
        if (objectContainer.hasObject(id) || objectStorage.hasData(id)) {
            VxMainClass.LOGGER.error("Generated a duplicate UUID during spawning: {}. This is highly unlikely. Aborting.", id);
            return Optional.empty();
        }

        T newObject = type.create(world.getLevel());
        if (newObject == null) {
            VxMainClass.LOGGER.error("Factory for {} returned null.", type.getTypeId());
            return Optional.empty();
        }

        newObject.setPhysicsId(id);
        newObject.setInitialTransform(transform);
        configurator.accept(newObject);

        objectContainer.add(newObject);

        return Optional.of(newObject);
    }

    public void removeObject(UUID id, VxRemovalReason reason) {
        IPhysicsObject obj = objectContainer.remove(id);
        if (obj == null) {
            if (reason == VxRemovalReason.DISCARD) {
                objectStorage.removeData(id);
            }
            return;
        }

        if (reason == VxRemovalReason.SAVE) {
            objectStorage.saveData(obj);
        }

        world.getConstraintManager().removeConstraintsForObject(id, reason == VxRemovalReason.DISCARD);
    }

    public static ChunkPos getObjectChunkPos(IPhysicsObject obj) {
        VxTransform transform = obj.getCurrentTransform();
        return new ChunkPos(
                SectionPos.posToSectionCoord(transform.getTranslation().x()),
                SectionPos.posToSectionCoord(transform.getTranslation().z())
        );
    }

    public Optional<IPhysicsObject> getObject(UUID id) {
        return objectContainer.get(id);
    }

    public CompletableFuture<IPhysicsObject> getOrLoadObject(UUID id) {
        if (id == null) {
            return CompletableFuture.completedFuture(null);
        }
        Optional<IPhysicsObject> loadedObject = getObject(id);
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

    public VxPhysicsUpdater getPhysicsUpdater() {
        return physicsUpdater;
    }

    public VxObjectNetworkDispatcher getNetworkDispatcher() {
        return networkDispatcher;
    }

    public RidingManager getRidingManager() {
        return this.ridingManager;
    }
}