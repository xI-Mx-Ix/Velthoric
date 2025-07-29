package net.xmx.vortex.physics.object.physicsobject.manager;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.xmx.vortex.physics.object.physicsobject.IPhysicsObject;
import net.xmx.vortex.physics.object.physicsobject.pcmd.ActivateBodyCommand;
import net.xmx.vortex.physics.world.VxPhysicsWorld;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class VxObjectContainer {
    
    private final VxPhysicsWorld world;
    private final Map<UUID, IPhysicsObject> managedObjects = new ConcurrentHashMap<>();
    private final Int2ObjectMap<UUID> bodyIdToUuidMap = new Int2ObjectOpenHashMap<>();
    private final Object bodyIdToUuidMapLock = new Object();
    private final Queue<IPhysicsObject> pendingActivationQueue = new ConcurrentLinkedQueue<>();

    public VxObjectContainer(VxPhysicsWorld world) {
        this.world = world;
    }

    public void add(IPhysicsObject obj) {
        if (obj == null || managedObjects.containsKey(obj.getPhysicsId())) {
            return;
        }

        managedObjects.put(obj.getPhysicsId(), obj);
        obj.initializePhysics(world);
        pendingActivationQueue.add(obj);

        world.getConstraintManager().getDataSystem().onDependencyLoaded(obj.getPhysicsId());
    }

    public IPhysicsObject remove(UUID id) {
        IPhysicsObject obj = managedObjects.remove(id);
        if (obj != null) {
            obj.markRemoved();
            obj.removeFromPhysics(world);
            unlinkBodyId(obj.getBodyId());
        }
        return obj;
    }
    
    public void processPendingActivations() {
        if (pendingActivationQueue.isEmpty()) return;
        
        IPhysicsObject obj;
        while ((obj = pendingActivationQueue.poll()) != null) {
            if (!obj.isRemoved() && obj.getBodyId() != 0) {
                 world.queueCommand(new ActivateBodyCommand(obj.getBodyId()));
            }
        }
    }

    public void clear() {
        managedObjects.values().forEach(obj -> obj.removeFromPhysics(world));
        managedObjects.clear();
        synchronized (bodyIdToUuidMapLock) {
            bodyIdToUuidMap.clear();
        }
        pendingActivationQueue.clear();
    }

    public void linkBodyId(int bodyId, UUID objectId) {
        synchronized (bodyIdToUuidMapLock) {
            this.bodyIdToUuidMap.put(bodyId, objectId);
        }
    }

    public void unlinkBodyId(int bodyId) {
        synchronized (bodyIdToUuidMapLock) {
            this.bodyIdToUuidMap.remove(bodyId);
        }
    }

    public Optional<IPhysicsObject> getByBodyId(int bodyId) {
        UUID objectId;
        synchronized (bodyIdToUuidMapLock) {
            objectId = bodyIdToUuidMap.get(bodyId);
        }

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
        return Collections.unmodifiableCollection(managedObjects.values());
    }
}