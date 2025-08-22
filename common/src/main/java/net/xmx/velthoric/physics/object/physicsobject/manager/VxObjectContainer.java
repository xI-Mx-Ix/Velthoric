package net.xmx.velthoric.physics.object.physicsobject.manager;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.xmx.velthoric.physics.object.physicsobject.VxAbstractBody;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VxObjectContainer {

    private final VxPhysicsWorld world;
    private final Map<UUID, VxAbstractBody> managedObjects = new ConcurrentHashMap<>();
    private final Int2ObjectMap<UUID> bodyIdToUuidMap = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());

    public VxObjectContainer(VxPhysicsWorld world) {
        this.world = world;
    }

    public void add(VxAbstractBody obj) {
        if (obj == null) return;
        managedObjects.computeIfAbsent(obj.getPhysicsId(), id -> {
            if (obj.getBodyId() != 0) {
                linkBodyId(obj.getBodyId(), id);
            }
            world.getConstraintManager().getDataSystem().onDependencyLoaded(id);
            return obj;
        });
    }

    public VxAbstractBody remove(UUID id) {
        VxAbstractBody obj = managedObjects.remove(id);
        if (obj != null) {
            if (obj.getBodyId() != 0) {
                unlinkBodyId(obj.getBodyId());
            }
        }
        return obj;
    }

    public void clear() {
        managedObjects.clear();
        bodyIdToUuidMap.clear();
    }

    public void linkBodyId(int bodyId, UUID objectId) {
        if (bodyId == 0) {
            new Throwable("Attempted to link invalid Body ID 0").printStackTrace();
            return;
        }
        this.bodyIdToUuidMap.put(bodyId, objectId);
    }

    public void unlinkBodyId(int bodyId) {
        if (bodyId == 0) {
            new Throwable("Attempted to unlink invalid Body ID 0").printStackTrace();
            return;
        }
        this.bodyIdToUuidMap.remove(bodyId);
    }

    public Optional<VxAbstractBody> getByBodyId(int bodyId) {
        UUID objectId = bodyIdToUuidMap.get(bodyId);
        if (objectId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(managedObjects.get(objectId));
    }

    public Optional<VxAbstractBody> get(UUID id) {
        return Optional.ofNullable(managedObjects.get(id));
    }

    public boolean hasObject(UUID id) {
        return managedObjects.containsKey(id);
    }

    public Collection<VxAbstractBody> getAllObjects() {
        return managedObjects.values();
    }
}