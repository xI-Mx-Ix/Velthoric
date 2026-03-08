/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.body;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    }
}