/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.behavior;

import net.minecraft.server.level.ServerLevel;
import net.xmx.velthoric.core.body.client.VxClientBodyDataStore;
import net.xmx.velthoric.core.body.client.VxClientBodyManager;
import net.xmx.velthoric.core.body.server.VxServerBodyDataStore;
import net.xmx.velthoric.core.body.VxBody;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;

/**
 * The core interface for a modular behavior in the composition-based physics system.
 * <p>
 * A {@code VxBehavior} is a stateless "system" in the ECS sense. It iterates over all bodies
 * that have its corresponding {@link VxBehaviorId} bit set and performs logic on the SoA
 * data arrays directly. This design maximizes cache locality and avoids per-body virtual dispatch.
 * <p>
 * <b>Lifecycle:</b>
 * <ol>
 *     <li>{@link #onAttached} — Called when a body gains this behavior. Initialize per-body data here.</li>
 *     <li>{@link #onPrePhysicsTick} — Called before each physics simulation step (physics thread).</li>
 *     <li>{@link #onPhysicsTick} — Called after each physics simulation step (physics thread).</li>
 *     <li>{@link #onServerTick} — Called on the main server thread each game tick.</li>
 *     <li>{@link #onClientTick} — Called on the client thread each game tick.</li>
 *     <li>{@link #onDetached} — Called when a body loses this behavior. Clean up per-body data here.</li>
 * </ol>
 *
 * <b>Important:</b> Implementations should iterate the {@code dataStore.bodies[]} array themselves
 * and check {@code dataStore.behaviorBits[i]} using their {@link VxBehaviorId#getMask()} to filter
 * relevant bodies.
 *
 * @author xI-Mx-Ix
 */
public interface VxBehavior {

    /**
     * @return The unique identifier for this behavior, used for bitmask membership checks.
     */
    VxBehaviorId getId();

    /**
     * Called when a body gains this behavior.
     * <p>
     * Use this to initialize any behavior-specific data at the body's DataStore index.
     * This method is called on the side where the body is constructed.
     *
     * @param index The data store index of the body.
     * @param body  The body instance.
     */
    default void onAttached(int index, VxBody body) {
    }

    /**
     * Called when a body loses this behavior (either through explicit removal or body destruction).
     * <p>
     * Use this to clean up any behavior-specific data at the body's DataStore index.
     *
     * @param index The data store index of the body.
     * @param body  The body instance.
     */
    default void onDetached(int index, VxBody body) {
    }

    /**
     * Called before each physics simulation step on the physics thread.
     * <p>
     * Implementations should iterate the data store and process bodies that have this behavior's bit set.
     *
     * @param world The physics world instance.
     * @param store The server-side SoA data store.
     */
    default void onPrePhysicsTick(VxPhysicsWorld world, VxServerBodyDataStore store) {
    }

    /**
     * Called after each physics simulation step on the physics thread.
     * <p>
     * Implementations should iterate the data store and process bodies that have this behavior's bit set.
     *
     * @param world The physics world instance.
     * @param store The server-side SoA data store.
     */
    default void onPhysicsTick(VxPhysicsWorld world, VxServerBodyDataStore store) {
    }

    /**
     * Called on each game tick on the server thread.
     *
     * @param level The server level instance.
     * @param store The server-side SoA data store.
     */
    default void onServerTick(ServerLevel level, VxServerBodyDataStore store) {
    }

    /**
     * Called once per client game tick for all bodies that have this behavior attached.
     * <p>
     * Use this to process client-side logic or send updates to the server.
     *
     * @param manager The client body manager.
     * @param store   The client data store.
     */
    default void onClientTick(VxClientBodyManager manager, VxClientBodyDataStore store) {
    }
}