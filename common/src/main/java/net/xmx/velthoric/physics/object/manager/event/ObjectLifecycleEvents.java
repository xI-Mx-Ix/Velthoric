/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.manager.event;

import net.minecraft.world.level.Level;
import net.xmx.velthoric.event.api.VxChunkEvent;
import net.xmx.velthoric.event.api.VxLevelEvent;
import net.xmx.velthoric.physics.object.type.VxBody;
import net.xmx.velthoric.physics.object.manager.VxObjectManager;
import net.xmx.velthoric.physics.object.manager.VxRemovalReason;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.List;
import java.util.Optional;

/**
 * Handles game lifecycle events to manage physics objects accordingly.
 * This class listens for chunk loads/unloads and level saves to trigger
 * loading, saving, and removal of physics objects from the world.
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
        if (world != null && world.getObjectManager() != null) {
            return Optional.of(world.getObjectManager());
        }
        return Optional.empty();
    }

    /**
     * Called when a chunk is loaded. Initiates the loading of all physics objects within that chunk.
     *
     * @param event The chunk load event data.
     */
    private static void onChunkLoad(VxChunkEvent.Load event) {
        getObjectManager(event.getLevel()).ifPresent(manager ->
                manager.getObjectStorage().loadObjectsInChunk(event.getChunkPos())
        );
    }

    /**
     * Called when a chunk is unloaded. Removes all physics objects within that chunk from the active
     * simulation and queues them for saving.
     *
     * @param event The chunk unload event data.
     */
    private static void onChunkUnload(VxChunkEvent.Unload event) {
        getObjectManager(event.getLevel()).ifPresent(manager -> {
            List<VxBody> objectsInChunk = manager.getObjectsInChunk(event.getChunkPos());
            // Create a copy to avoid ConcurrentModificationException
            for (VxBody obj : List.copyOf(objectsInChunk)) {
                // Remove the object with the SAVE reason, which ensures it gets stored before being removed.
                manager.removeObject(obj.getPhysicsId(), VxRemovalReason.SAVE);
            }
        });
    }

    /**
     * Called when the level is being saved. Triggers the saving of all active physics objects
     * and any dirty region files. This is dispatched to the physics thread for safety.
     *
     * @param event The level save event data.
     */
    private static void onLevelSave(VxLevelEvent.Save event) {
        getObjectManager(event.getLevel()).ifPresent(manager -> {
            VxPhysicsWorld world = manager.getPhysicsWorld();
            // Ensure the world is actually running and queue the save operation
            // to be executed on the physics thread for thread safety.
            if (world != null && world.isRunning()) {
                world.execute(() -> {
                    // Now we are on the physics thread, it is safe to access physics data
                    manager.getAllObjects().forEach(manager.getObjectStorage()::storeObject);
                    manager.getObjectStorage().saveDirtyRegions();
                });
            }
        });
    }
}