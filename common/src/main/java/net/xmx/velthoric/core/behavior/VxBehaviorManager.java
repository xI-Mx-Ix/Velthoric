/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.behavior;

import net.minecraft.server.level.ServerLevel;
import net.xmx.velthoric.core.body.server.VxServerBodyDataStore;
import net.xmx.velthoric.core.body.type.VxBody;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;
import net.xmx.velthoric.init.VxMainClass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Central orchestrator for the behavior-based composition system.
 * <p>
 * This manager is responsible for:
 * <ul>
 *     <li>Registering global behavior instances.</li>
 *     <li>Dispatching lifecycle events (attach/detach) when behaviors are added to or removed from bodies.</li>
 *     <li>Iterating behaviors during tick phases (pre-physics, physics, server tick).</li>
 * </ul>
 * <p>
 * Each {@link VxPhysicsWorld} owns one instance of this manager.
 * The behaviors are iterated in registration order, which allows control over execution priority.
 *
 * @author xI-Mx-Ix
 */
public class VxBehaviorManager {

    /**
     * The data store backing this manager's physics world.
     */
    private final VxServerBodyDataStore dataStore;

    /**
     * All registered behaviors, iterated in order during tick phases.
     * This list is populated during initialization and should not be modified during simulation.
     */
    private final List<VxBehavior> behaviors = new ArrayList<>();

    /**
     * An unmodifiable view of the behavior list for external iteration.
     */
    private final List<VxBehavior> behaviorsView = Collections.unmodifiableList(behaviors);

    /**
     * Constructs a new behavior manager for the given data store.
     *
     * @param dataStore The server-side SoA data store.
     */
    public VxBehaviorManager(VxServerBodyDataStore dataStore) {
        this.dataStore = dataStore;
    }

    /**
     * Registers a behavior with this manager.
     * <p>
     * Behaviors should be registered during world initialization, before any bodies are created.
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
        if (index == -1) return;

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
        if (index == -1) return;

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
        if (index == -1) return;

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
        if (index == -1) return false;
        return behaviorId.isSet(dataStore.behaviorBits[index]);
    }

    // ================================================================================
    // Tick Dispatch
    // ================================================================================

    /**
     * Dispatches the pre-physics-tick event to all registered behaviors.
     * Called on the physics thread before the Jolt simulation step.
     *
     * @param world The physics world instance.
     */
    public void onPrePhysicsTick(VxPhysicsWorld world) {
        for (int i = 0, size = behaviors.size(); i < size; i++) {
            behaviors.get(i).onPrePhysicsTick(world, dataStore);
        }
    }

    /**
     * Dispatches the post-physics-tick event to all registered behaviors.
     * Called on the physics thread after the Jolt simulation step.
     *
     * @param world The physics world instance.
     */
    public void onPhysicsTick(VxPhysicsWorld world) {
        for (int i = 0, size = behaviors.size(); i < size; i++) {
            behaviors.get(i).onPhysicsTick(world, dataStore);
        }
    }

    /**
     * Dispatches the server-tick event to all registered behaviors.
     * Called on the main server thread.
     *
     * @param level The server level instance.
     */
    public void onServerTick(ServerLevel level) {
        for (int i = 0, size = behaviors.size(); i < size; i++) {
            behaviors.get(i).onServerTick(level, dataStore);
        }
    }

    // ================================================================================
    // Accessors
    // ================================================================================

    /**
     * @return An unmodifiable view of all registered behaviors.
     */
    public List<VxBehavior> getBehaviors() {
        return behaviorsView;
    }
}