/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.manager.event;

import net.minecraft.world.level.Level;
import net.xmx.velthoric.event.api.VxChunkEvent;
import net.xmx.velthoric.event.api.VxLevelEvent;
import net.xmx.velthoric.physics.object.manager.VxObjectManager;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.Optional;

/**
 * Handles game lifecycle events to trigger physics object management.
 * This class acts as a simple bridge between game events and the VxObjectManager,
 * delegating all logic to the manager itself.
 *
 * @author xI-Mx-Ix
 */
public class ObjectLifecycleEvents {

    /**
     * Registers all necessary event listeners for managing the object lifecycle.
     */
    public static void registerEvents() {
        VxChunkEvent.Load.EVENT.register(ObjectLifecycleEvents::onChunkLoad);
        VxChunkEvent.Unload.EVENT.register(ObjectLifecycleEvents::onChunkUnload);
        VxLevelEvent.Save.EVENT.register(ObjectLifecycleEvents::onLevelSave);
    }

    /**
     * Safely retrieves the {@link VxObjectManager} for a given level.
     *
     * @param level The level to get the object manager from.
     * @return An Optional containing the manager if it exists, otherwise an empty Optional.
     */
    private static Optional<VxObjectManager> getObjectManager(Level level) {
        if (level.isClientSide()) {
            return Optional.empty();
        }
        VxPhysicsWorld world = VxPhysicsWorld.get(level.dimension());
        return (world != null) ? Optional.ofNullable(world.getObjectManager()) : Optional.empty();
    }

    /**
     * Called when a chunk is loaded. Delegates to the object storage to load objects.
     *
     * @param event The chunk load event data.
     */
    private static void onChunkLoad(VxChunkEvent.Load event) {
        getObjectManager(event.getLevel()).ifPresent(manager ->
                manager.getObjectStorage().loadObjectsInChunk(event.getChunkPos())
        );
    }

    /**
     * Called when a chunk is unloaded. Delegates to the object manager to handle the unloading.
     *
     * @param event The chunk unload event data.
     */
    private static void onChunkUnload(VxChunkEvent.Unload event) {
        getObjectManager(event.getLevel()).ifPresent(manager ->
                manager.onChunkUnload(event.getChunkPos())
        );
    }

    /**
     * Called when the level is being saved. Delegates to the object manager to save all data.
     *
     * @param event The level save event data.
     */
    private static void onLevelSave(VxLevelEvent.Save event) {
        getObjectManager(event.getLevel()).ifPresent(VxObjectManager::saveAll);
    }
}