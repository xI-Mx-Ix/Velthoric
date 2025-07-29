package net.xmx.vortex.physics.object.physicsobject.manager;

import net.xmx.vortex.physics.object.physicsobject.manager.persistence.VxObjectStorage;
import net.xmx.vortex.physics.object.physicsobject.manager.registry.VxObjectRegistry;

public class VxUnsafe {

    private final VxObjectManager manager;

    public VxUnsafe(VxObjectManager manager) {
        this.manager = manager;
    }

    public VxObjectContainer getObjectContainer() {
        return manager.getObjectContainer();
    }

    public VxObjectStorage getObjectStorage() {
        return manager.getObjectStorage();
    }

    public VxPhysicsUpdater getPhysicsUpdater() {
        return manager.getPhysicsUpdater();
    }

    public VxObjectNetworkDispatcher getNetworkDispatcher() {
        return manager.getNetworkDispatcher();
    }

    public VxObjectRegistry getObjectRegistry() {
        return manager.getObjectRegistry();
    }
}