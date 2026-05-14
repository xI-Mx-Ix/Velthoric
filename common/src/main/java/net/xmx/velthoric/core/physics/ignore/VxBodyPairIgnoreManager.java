/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.physics.ignore;

import net.minecraft.world.level.ChunkPos;
import net.xmx.velthoric.core.body.VxBody;
import net.xmx.velthoric.core.body.VxRemovalReason;
import net.xmx.velthoric.core.body.server.VxServerBodyManager;
import net.xmx.velthoric.core.body.tracking.VxSpatialManager;
import net.xmx.velthoric.core.persistence.VxChunkPersistenceHandler;
import net.xmx.velthoric.core.physics.ignore.persistence.VxIgnoreStorage;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;
import net.xmx.velthoric.jni.BodyPairIgnoreHandler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates collision filters between bodies using a dependency-based activation system.
 * <p>
 * This manager mirrors the architecture of the constraint system. Ignores are stored 
 * as independent objects and only activated in the native Jolt engine once both 
 * participating bodies are loaded in memory.
 *
 * @author xI-Mx-Ix
 */
public class VxBodyPairIgnoreManager implements VxChunkPersistenceHandler {

    /**
     * The physics world instance this manager operates within.
     */
    private final VxPhysicsWorld world;

    /**
     * Dedicated storage handler for persisting collision ignore data to disk.
     */
    private final VxIgnoreStorage ignoreStorage;

    /**
     * Set of all currently active persistent ignores, grouped by chunk.
     */
    private final Map<Long, Set<VxBodyPairIgnore>> chunkToIgnores = new ConcurrentHashMap<>();

    /**
     * Ignores that are waiting for one or both bodies to be loaded.
     */
    private final Map<UUID, Set<VxBodyPairIgnore>> pendingIgnores = new ConcurrentHashMap<>();

    /**
     * Constructs a new ignore manager for the specified world.
     *
     * @param world The physics world to manage ignores for.
     */
    public VxBodyPairIgnoreManager(VxPhysicsWorld world) {
        this.world = world;
        this.ignoreStorage = new VxIgnoreStorage(world.getLevel());
    }

    /**
     * Registers a new collision ignore.
     *
     * @param id1        First body UUID.
     * @param id2        Second body UUID.
     * @param persistent Whether to save this ignore to disk.
     */
    public void ignorePair(UUID id1, UUID id2, boolean persistent) {
        VxBodyPairIgnore ignore = new VxBodyPairIgnore(id1, id2);
        
        if (persistent) {
            // Determine which chunk owns this ignore.
            // We use the chunk of body1 if it exists, otherwise a default fallback.
            VxServerBodyManager manager = world.getBodyManager();
            VxBody b1 = manager.getVxBody(id1);
            int idx = b1 != null ? b1.getDataStoreIndex() : -1;
            long chunkKey = (idx != -1) ? manager.getDataStore().serverCurrent().chunkKey[idx] : 0;
            
            // Fallback: If the body was just spawned, it might not have its chunk key in the SoA yet.
            if (chunkKey == 0 || chunkKey == Long.MAX_VALUE) {
                if (b1 != null) {
                    chunkKey = VxSpatialManager.calculateChunkKey(
                            b1.getTransform().getTranslation().x(), 
                            b1.getTransform().getTranslation().z()
                    );
                }
            }
            
            if (chunkKey != 0) {
                chunkToIgnores.computeIfAbsent(chunkKey, k -> ConcurrentHashMap.newKeySet()).add(ignore);
            }
        }

        addPendingIgnore(ignore);
    }

    /**
     * Removes an existing collision ignore and synchronizes the change with the native engine.
     *
     * @param id1 Unique identifier of the first body.
     * @param id2 Unique identifier of the second body.
     */
    public void removeIgnorePair(UUID id1, UUID id2) {
        VxBodyPairIgnore ignore = new VxBodyPairIgnore(id1, id2);
        
        // Remove from all runtime and persistence tracking
        pendingIgnores.values().forEach(set -> set.remove(ignore));
        chunkToIgnores.values().forEach(set -> set.remove(ignore));

        // Synchronize removal with the native simulation if both bodies are currently active
        VxServerBodyManager manager = world.getBodyManager();
        VxBody b1 = manager.getVxBody(id1);
        VxBody b2 = manager.getVxBody(id2);

        if (b1 != null && b2 != null && b1.getBodyId() != 0 && b2.getBodyId() != 0) {
            BodyPairIgnoreHandler handler = world.getBodyPairIgnoreHandler();
            if (handler != null) {
                handler.removeIgnorePair(b1.getBodyId(), b2.getBodyId());
            }
        }
    }

    /**
     * Registers an ignore for dependency tracking. 
     * <p>
     * The ignore will remain in a pending state until both bodies are verified 
     * to be present in the active physics simulation.
     *
     * @param ignore The ignore pair data.
     */
    public void addPendingIgnore(VxBodyPairIgnore ignore) {
        pendingIgnores.computeIfAbsent(ignore.getBody1Id(), k -> ConcurrentHashMap.newKeySet()).add(ignore);
        pendingIgnores.computeIfAbsent(ignore.getBody2Id(), k -> ConcurrentHashMap.newKeySet()).add(ignore);
        
        // Immediate attempt to activate if bodies are already present
        checkAndActivate(ignore);
    }

    /**
     * Verifies if both participants of an ignore pair are loaded and activates 
     * the filter in the native Jolt engine if they are.
     *
     * @param ignore The ignore pair to verify.
     */
    private void checkAndActivate(VxBodyPairIgnore ignore) {
        VxServerBodyManager manager = world.getBodyManager();
        VxBody b1 = manager.getVxBody(ignore.getBody1Id());
        VxBody b2 = manager.getVxBody(ignore.getBody2Id());

        if (b1 != null && b2 != null && b1.getBodyId() != 0 && b2.getBodyId() != 0) {
            BodyPairIgnoreHandler handler = world.getBodyPairIgnoreHandler();
            if (handler != null) {
                handler.ignorePair(b1.getBodyId(), b2.getBodyId());
            }
        }
    }

    /**
     * Called when a body is added. Re-activates any relevant ignores.
     */
    public void onBodyAdded(VxBody body) {
        Set<VxBodyPairIgnore> affected = pendingIgnores.get(body.getPhysicsId());
        if (affected != null) {
            for (VxBodyPairIgnore ignore : affected) {
                checkAndActivate(ignore);
            }
        }
    }

    /**
     * Triggered when a body is removed from the physics world.
     * <p>
     * If the removal is permanent (DISCARD), all associated collision ignores 
     * are purged from memory and persistence.
     *
     * @param body   The body being removed.
     * @param reason The reason for removal (UNLOAD, DISCARD, etc.).
     */
    public void onBodyRemoved(VxBody body, VxRemovalReason reason) {
        if (body.getBodyId() != 0) {
            BodyPairIgnoreHandler handler = world.getBodyPairIgnoreHandler();
            if (handler != null) {
                handler.onBodyRemoved(body.getBodyId());
            }
        }

        if (reason == VxRemovalReason.DISCARD) {
            UUID id = body.getPhysicsId();
            Set<VxBodyPairIgnore> affected = pendingIgnores.remove(id);
            if (affected != null) {
                for (VxBodyPairIgnore ignore : affected) {
                    // Cleanup cross-references in the dependency map
                    UUID otherId = ignore.getBody1Id().equals(id) ? ignore.getBody2Id() : ignore.getBody1Id();
                    Set<VxBodyPairIgnore> otherSet = pendingIgnores.get(otherId);
                    if (otherSet != null) otherSet.remove(ignore);
                    
                    // Permanent removal from chunk-based storage
                    chunkToIgnores.values().forEach(set -> set.remove(ignore));
                }
            }
        }
    }

    /**
     * Orchestrates the loading of collision ignores during a chunk load event.
     * <p>
     * This implementation triggers an asynchronous disk read and schedules 
     * the processing of results on the physics thread.
     *
     * @param pos The position of the chunk being loaded.
     */
    @Override
    public void onChunkLoad(ChunkPos pos) {
        ignoreStorage.loadChunk(pos).thenAccept(ignores -> {
            world.execute(() -> {
                if (ignores.isEmpty()) return;
                
                Set<VxBodyPairIgnore> set = chunkToIgnores.computeIfAbsent(pos.toLong(), k -> ConcurrentHashMap.newKeySet());
                for (VxBodyPairIgnore ignore : ignores) {
                    set.add(ignore);
                    addPendingIgnore(ignore);
                }
            });
        });
    }

    /**
     * Handles the unloading of collision ignores when a chunk is removed from memory.
     * <p>
     * Cleans up persistence tracking and removes ignores from memory if no 
     * participating bodies remain loaded.
     *
     * @param pos The position of the chunk being unloaded.
     */
    @Override
    public void onChunkUnload(ChunkPos pos) {
        Set<VxBodyPairIgnore> ignores = chunkToIgnores.remove(pos.toLong());
        if (ignores != null) {
            for (VxBodyPairIgnore ignore : ignores) {
                removeIfOrphaned(ignore);
            }
        }
    }

    /**
     * Internal cleanup logic to remove an ignore from the dependency tracking 
     * if neither participating body is currently loaded in memory.
     *
     * @param ignore The ignore pair to check.
     */
    private void removeIfOrphaned(VxBodyPairIgnore ignore) {
        VxServerBodyManager manager = world.getBodyManager();
        boolean b1Loaded = manager.getVxBody(ignore.getBody1Id()) != null;
        boolean b2Loaded = manager.getVxBody(ignore.getBody2Id()) != null;

        if (!b1Loaded && !b2Loaded) {
            pendingIgnores.getOrDefault(ignore.getBody1Id(), Collections.emptySet()).remove(ignore);
            pendingIgnores.getOrDefault(ignore.getBody2Id(), Collections.emptySet()).remove(ignore);
        }
    }

    /**
     * Retrieves all persistent ignores assigned to a specific chunk.
     *
     * @param chunkKey The long-encoded chunk position.
     * @return A collection of ignores belonging to the chunk.
     */
    public Collection<VxBodyPairIgnore> getIgnoresForChunk(long chunkKey) {
        return chunkToIgnores.getOrDefault(chunkKey, Collections.emptySet());
    }

    /**
     * Serializes and queues all persistent collision ignores for a chunk to disk.
     *
     * @param pos The position of the chunk being saved.
     */
    @Override
    public void onChunkSave(ChunkPos pos) {
        Set<VxBodyPairIgnore> ignores = chunkToIgnores.get(pos.toLong());
        if (ignores != null) {
            ignoreStorage.saveChunk(pos, ignores);
        } else {
            // Explicitly save an empty list to clear stale data on disk
            ignoreStorage.saveChunk(pos, Collections.emptyList());
        }
    }

    /**
     * Blocks or queues a flush of all pending persistence tasks to disk.
     *
     * @param block If true, the call blocks until I/O is complete.
     */
    @Override
    public void flush(boolean block) {
        ignoreStorage.flush(block);
    }

    /**
     * Shuts down the manager, ensuring all data is flushed and registries are cleared.
     */
    public void shutdown() {
        flush(true);
        ignoreStorage.shutdown();
        chunkToIgnores.clear();
        pendingIgnores.clear();
    }
}