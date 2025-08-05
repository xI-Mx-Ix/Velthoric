package net.xmx.vortex.physics.object.physicsobject.manager;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.xmx.vortex.init.VxMainClass;
import net.xmx.vortex.physics.object.physicsobject.IPhysicsObject;
import net.xmx.vortex.physics.world.VxPhysicsWorld;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VxObjectContainer {

    private final VxPhysicsWorld world;
    private final Map<UUID, IPhysicsObject> managedObjects = new ConcurrentHashMap<>();

    private final Int2ObjectMap<UUID> bodyIdToUuidMap = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());

    public VxObjectContainer(VxPhysicsWorld world) {
        this.world = world;
    }

    public void add(IPhysicsObject obj) {
        if (obj == null) return;

        managedObjects.computeIfAbsent(obj.getPhysicsId(), id -> {
            obj.initializePhysics(world);
            world.getConstraintManager().getDataSystem().onDependencyLoaded(id);
            return obj;
        });
    }

    public IPhysicsObject remove(UUID id) {
        IPhysicsObject obj = managedObjects.remove(id);
        if (obj != null) {
            obj.markRemoved();
            obj.removeFromPhysics(world);
            if (obj.getBodyId() != 0) {
                unlinkBodyId(obj.getBodyId());
            }
        }
        return obj;
    }

    public void clear() {
        managedObjects.values().parallelStream().forEach(obj -> obj.removeFromPhysics(world));
        managedObjects.clear();
        bodyIdToUuidMap.clear();
    }

    public void linkBodyId(int bodyId, UUID objectId) {
        VxMainClass.LOGGER.debug("[VxObjectContainer-DEBUG] Linking Body ID {} to UUID {}", bodyId, objectId);
        if (bodyId == 0) {
            new Throwable("Attempted to link invalid Body ID 0").printStackTrace();
        }
        this.bodyIdToUuidMap.put(bodyId, objectId);
    }

    public void unlinkBodyId(int bodyId) {
        VxMainClass.LOGGER.debug("[VxObjectContainer-DEBUG] Unlinking Body ID {}", bodyId);
        if (bodyId == 0) {
            new Throwable("Attempted to unlink invalid Body ID 0").printStackTrace();
        }
        this.bodyIdToUuidMap.remove(bodyId);
    }

    public Optional<IPhysicsObject> getByBodyId(int bodyId) {
        UUID objectId = bodyIdToUuidMap.get(bodyId);
        if (objectId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(managedObjects.get(objectId));
    }

    public Optional<IPhysicsObject> get(UUID id) {
        return Optional.ofNullable(managedObjects.get(id));
    }

    public boolean hasObject(UUID id) {
        return managedObjects.containsKey(id);
    }

    public Collection<IPhysicsObject> getAllObjects() {
        return managedObjects.values();
    }
}