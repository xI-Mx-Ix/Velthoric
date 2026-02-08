/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.body.manager;

import net.xmx.velthoric.core.body.listener.VxBodyLifecycleListener;
import net.xmx.velthoric.core.body.type.VxBody;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Base class for managing the lifecycle and storage of physics bodies.
 * Provides a unified registry and listener system for both server and client managers.
 *
 * @author xI-Mx-Ix
 */
public abstract class VxAbstractBodyManager {

    /**
     * Primary registry of active bodies, mapped by their persistent unique identifier (UUID).
     * This map is thread-safe to allow concurrent access.
     */
    protected final Map<UUID, VxBody> managedBodies = new ConcurrentHashMap<>();

    /**
     * A thread-safe list of lifecycle listeners that react to body addition and removal.
     */
    protected final List<VxBodyLifecycleListener> lifecycleListeners = new CopyOnWriteArrayList<>();

    /**
     * Registers a new lifecycle listener.
     *
     * @param listener The listener to register.
     */
    public void addLifecycleListener(VxBodyLifecycleListener listener) {
        this.lifecycleListeners.add(listener);
    }

    /**
     * Unregisters an existing lifecycle listener.
     *
     * @param listener The listener to remove.
     */
    public void removeLifecycleListener(VxBodyLifecycleListener listener) {
        this.lifecycleListeners.remove(listener);
    }

    /**
     * Notifies all registered listeners that a body has been added.
     *
     * @param body The added body.
     */
    protected void notifyBodyAdded(VxBody body) {
        for (VxBodyLifecycleListener listener : lifecycleListeners) {
            listener.onBodyAdded(body);
        }
    }

    /**
     * Notifies all registered listeners that a body is about to be removed.
     *
     * @param body The body being removed.
     */
    protected void notifyBodyRemoved(VxBody body) {
        for (VxBodyLifecycleListener listener : lifecycleListeners) {
            listener.onBodyRemoved(body);
        }
    }

    /**
     * Retrieves a managed body by its persistent UUID.
     *
     * @param id The unique identifier.
     * @return The body, or null if it is not currently loaded.
     */
    @Nullable
    public VxBody getVxBody(UUID id) {
        return managedBodies.get(id);
    }

    /**
     * Returns a collection of all currently active bodies.
     *
     * @return A collection view of all managed bodies.
     */
    public Collection<VxBody> getAllBodies() {
        return managedBodies.values();
    }

    /**
     * Clears all managed bodies and listeners.
     */
    protected void clearInternal() {
        managedBodies.clear();
        lifecycleListeners.clear();
    }
}