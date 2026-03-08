/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.behavior;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.xmx.velthoric.core.behavior.impl.*;
import net.xmx.velthoric.core.body.VxBodyDataStore;
import net.xmx.velthoric.core.body.client.VxClientBodyDataStore;
import net.xmx.velthoric.core.body.client.VxClientBodyManager;
import net.xmx.velthoric.core.body.server.VxServerBodyDataStore;
import net.xmx.velthoric.core.body.VxBody;
import net.xmx.velthoric.core.mounting.behavior.VxMountBehavior;
import net.xmx.velthoric.core.network.internal.behavior.VxNetSyncBehavior;
import net.xmx.velthoric.core.network.synchronization.behavior.VxSyncBehavior;
import net.xmx.velthoric.core.persistence.behavior.VxPersistenceBehavior;
import net.xmx.velthoric.core.physics.buoyancy.behavior.VxBuoyancyBehavior;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;
import net.xmx.velthoric.init.VxMainClass;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Central singleton orchestrator for the behavior-based composition system.
 * <p>
 * This manager is responsible for:
 * <ul>
 *     <li>Registering global behavior instances.</li>
 *     <li>Dispatching lifecycle events (attach/detach) when behaviors are added to or removed from bodies.</li>
 *     <li>Iterating behaviors during tick phases (pre-physics, physics, server tick, client tick).</li>
 * </ul>
 * <p>
 * The behaviors are iterated in registration order, which allows control over execution priority.
 *
 * @author xI-Mx-Ix
 */
public final class VxBehaviorManager {

    /**
     * All registered behaviors, iterated in order during tick phases.
     */
    private final List<VxBehavior> behaviors = new ArrayList<>();

    /**
     * Level associated with this manager.
     */
    private Level level;

    /**
     * An unmodifiable view of the behavior list for external iteration.
     */
    private final List<VxBehavior> behaviorsView = Collections.unmodifiableList(behaviors);

    public VxBehaviorManager() {
    }


    /**
     * Initializes the behavior system with built-in server-side behaviors.
     */
    public void init(Level level, @Nullable VxPhysicsWorld physicsWorld) {
        this.level = level;
        if (getBehavior(VxBehaviors.NET_SYNC) != null) return;

        if (!level.isClientSide()) {
            registerBehavior(new VxRigidPhysicsBehavior());
            registerBehavior(new VxSoftPhysicsBehavior());
            registerBehavior(new VxPersistenceBehavior());
            if (physicsWorld != null) {
                registerBehavior(new VxBuoyancyBehavior(physicsWorld));
            }
            registerBehavior(new VxPhysicsSyncBehavior());
            registerBehavior(new VxNetSyncBehavior());
            registerBehavior(new VxTickBehavior());
        }

        if (getBehavior(VxBehaviors.MOUNTABLE) == null) {
            registerBehavior(new VxMountBehavior());
        }

        // Use consolidated sync behavior for both sides
        if (getBehavior(VxBehaviors.CUSTOM_DATA_SYNC) == null) {
            registerBehavior(new VxSyncBehavior());
        }
    }

    /**
     * @return The level associated with this manager.
     */
    public Level getLevel() {
        return level;
    }

    /**
     * Registers a behavior with this manager.
     * <p>
     * Behaviors should be registered during mod initialization.
     * Registration order determines tick execution order.
     *
     * @param behavior The behavior to register.
     */
    public void registerBehavior(VxBehavior behavior) {
        behaviors.add(behavior);
        VxMainClass.LOGGER.debug("Registered behavior: {}", behavior.getId());
    }

    // ================================================================================
    // Behavior Attachment API
    // ================================================================================

    /**
     * Attaches a behavior to a body by setting the corresponding bit in the data store.
     * <p>
     * This method updates the body's {@code behaviorBits} and calls the behavior's
     * {@link VxBehavior#onAttached} callback.
     *
     * @param body     The body to attach the behavior to.
     * @param behavior The behavior to attach.
     */
    public void attachBehavior(VxBody body, VxBehavior behavior) {
        int index = body.getDataStoreIndex();
        VxBodyDataStore dataStore = body.getDataStore();
        if (index == -1 || dataStore == null) return;

        long mask = behavior.getId().getMask();
        if ((dataStore.behaviorBits[index] & mask) != 0) {
            return; // Already attached
        }

        dataStore.behaviorBits[index] |= mask;
        behavior.onAttached(index, body);
    }

    /**
     * Attaches a behavior to a body using only the behavior's ID mask.
     * This is the fast path used during body construction when the behavior instance
     * is known from the registered list.
     *
     * @param body       The body to attach the behavior to.
     * @param behaviorId The ID of the behavior to attach.
     */
    public void attachBehavior(VxBody body, VxBehaviorId behaviorId) {
        int index = body.getDataStoreIndex();
        if (index == -1) return;

        // Find the registered behavior for this ID and delegate
        for (VxBehavior behavior : behaviors) {
            if (behavior.getId() == behaviorId) {
                attachBehavior(body, behavior);
                return;
            }
        }
    }

    /**
     * Detaches a behavior from a body by clearing the corresponding bit in the data store.
     *
     * @param body     The body to detach the behavior from.
     * @param behavior The behavior to detach.
     */
    public void detachBehavior(VxBody body, VxBehavior behavior) {
        int index = body.getDataStoreIndex();
        VxBodyDataStore dataStore = body.getDataStore();
        if (index == -1 || dataStore == null) return;

        long mask = behavior.getId().getMask();
        if ((dataStore.behaviorBits[index] & mask) == 0) {
            return; // Not attached
        }

        behavior.onDetached(index, body);
        dataStore.behaviorBits[index] &= ~mask;
    }

    /**
     * Detaches all behaviors from a body. Called during body removal.
     *
     * @param body The body being removed.
     */
    public void detachAllBehaviors(VxBody body) {
        int index = body.getDataStoreIndex();
        VxBodyDataStore dataStore = body.getDataStore();
        if (index == -1 || dataStore == null) return;

        long bits = dataStore.behaviorBits[index];
        if (bits == 0) return;

        for (VxBehavior behavior : behaviors) {
            if (behavior.getId().isSet(bits)) {
                behavior.onDetached(index, body);
            }
        }
        dataStore.behaviorBits[index] = 0;
    }

    /**
     * Checks if a body has a specific behavior attached.
     *
     * @param body       The body to check.
     * @param behaviorId The behavior ID to check for.
     * @return True if the behavior is attached to the body.
     */
    public boolean hasBehavior(VxBody body, VxBehaviorId behaviorId) {
        int index = body.getDataStoreIndex();
        VxBodyDataStore dataStore = body.getDataStore();
        if (index == -1 || dataStore == null) return false;
        return behaviorId.isSet(dataStore.behaviorBits[index]);
    }

    /**
     * Retrieves a registered behavior instance by its unique identifier.
     * <p>
     * This method iterates through the internal registry to find a behavior
     * that matches the provided ID. It automatically casts the result to the
     * requested subtype.
     * </p>
     *
     * @param behaviorId The unique ID of the behavior to find.
     * @param <T>        The specific type of the behavior (must extend {@link VxBehavior}).
     * @return The behavior instance if found, or {@code null} if no behavior with
     * this ID is registered.
     */
    @SuppressWarnings("unchecked")
    public <T extends VxBehavior> T getBehavior(VxBehaviorId behaviorId) {
        for (VxBehavior behavior : behaviors) {
            if (behavior.getId() == behaviorId) {
                return (T) behavior;
            }
        }
        return null;
    }

    // ================================================================================
    // Tick Dispatch
    // ================================================================================

    /**
     * Dispatches the pre-physics tick event to all registered behaviors.
     *
     * @param world The physics world.
     * @param store The server-side body data store.
     */
    public void onPrePhysicsTick(VxPhysicsWorld world, VxServerBodyDataStore store) {
        for (int i = 0, size = behaviors.size(); i < size; i++) {
            behaviors.get(i).onPrePhysicsTick(world, store);
        }
    }

    /**
     * Dispatches the physics tick event to all registered behaviors.
     *
     * @param world The physics world.
     * @param store The server-side body data store.
     */
    public void onPhysicsTick(VxPhysicsWorld world, VxServerBodyDataStore store) {
        for (int i = 0, size = behaviors.size(); i < size; i++) {
            behaviors.get(i).onPhysicsTick(world, store);
        }
    }

    /**
     * Dispatches the server tick event to all registered behaviors.
     *
     * @param level The server level.
     * @param store The server-side body data store.
     */
    public void onServerTick(ServerLevel level, VxServerBodyDataStore store) {
        for (int i = 0, size = behaviors.size(); i < size; i++) {
            behaviors.get(i).onServerTick(level, store);
        }
    }

    /**
     * Dispatches the client tick event to all registered behaviors.
     *
     * @param manager The client body manager.
     * @param store   The client data store.
     */
    public void onClientTick(VxClientBodyManager manager, VxClientBodyDataStore store) {
        for (int i = 0, size = behaviors.size(); i < size; i++) {
            behaviors.get(i).onClientTick(manager, store);
        }
    }

    /**
     * @return An unmodifiable list of all currently registered behaviors.
     */
    public List<VxBehavior> getBehaviors() {
        return behaviorsView;
    }
}