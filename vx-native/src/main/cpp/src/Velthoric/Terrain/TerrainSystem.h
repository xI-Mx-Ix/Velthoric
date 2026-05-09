/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 *
 * Author: xI-Mx-Ix
 */
#pragma once
#include <Jolt/Jolt.h>
#include <Jolt/Physics/Body/BodyInterface.h>
#include <cstdint>
#include "Velthoric/Terrain/State/TerrainStateManager.h"
#include "Velthoric/Terrain/Generation/TerrainGenerator.h"

namespace Velthoric {

/**
 * @brief Central native orchestrator for the Velthoric terrain physics system.
 *
 * Owns the TerrainStateManager and coordinates all terrain chunk operations
 * including body creation, shape updates, activation, deactivation, and cleanup.
 * All JNI bridge methods are implemented in the corresponding .cpp file.
 *
 * This class replaces the Java-side VxTerrainManager, VxChunkDataStore, and
 * VxTerrainJobSystem. It operates entirely in C++ to eliminate JNI overhead
 * for state management and body lifecycle operations.
 */
class TerrainSystem {
public:
    /**
     * @brief Constructs the terrain system with a reference to the Jolt body interface.
     *
     * @param bodyInterface Pointer to the locking Jolt BodyInterface for thread-safe body ops.
     * @param terrainLayer The Jolt object layer assigned to terrain bodies.
     */
    TerrainSystem(JPH::BodyInterface* bodyInterface, uint16_t terrainLayer);

    /**
     * @brief Destroys the terrain system and cleans up all remaining bodies.
     */
    ~TerrainSystem();

    /**
     * @brief Requests a terrain chunk, incrementing its reference count.
     *
     * If this is the first reference, the chunk is registered as PENDING_DATA.
     *
     * @param packedPos The bit-packed section coordinate.
     * @return True if this is a new chunk that needs voxel data from Java.
     */
    bool RequestChunk(int64_t packedPos);

    /**
     * @brief Releases a terrain chunk, decrementing its reference count.
     *
     * When the reference count reaches zero, the chunk's physics body is
     * destroyed and the entry is removed from the state manager.
     *
     * @param packedPos The bit-packed section coordinate.
     */
    void ReleaseChunk(int64_t packedPos);

    /**
     * @brief Activates a chunk, adding its body to the physics simulation.
     *
     * If the chunk has no body yet (still pending data), this is a no-op.
     * If the chunk is a placeholder, it signals that it should be prioritized.
     *
     * @param packedPos The bit-packed section coordinate.
     * @return True if the chunk is a placeholder and needs re-submission.
     */
    bool ActivateChunk(int64_t packedPos);

    /**
     * @brief Deactivates a chunk, removing its body from the physics simulation.
     *
     * The body is not destroyed; it can be re-activated later.
     *
     * @param packedPos The bit-packed section coordinate.
     */
    void DeactivateChunk(int64_t packedPos);

    /**
     * @brief Checks if a chunk should be prioritized for data submission.
     *
     * A chunk needs priority if it is a placeholder or not yet ready.
     *
     * @param packedPos The bit-packed section coordinate.
     * @return True if the chunk needs voxel data (re-)submission.
     */
    bool PrioritizeChunk(int64_t packedPos);

    /**
     * @brief Receives voxel box data from Java and creates or updates the physics body.
     *
     * This method generates the shape via TerrainGenerator, creates a new body
     * or updates an existing one, and transitions the chunk state accordingly.
     * If boxCount is 0, the chunk is marked as AIR_CHUNK.
     *
     * @param packedPos The bit-packed section coordinate.
     * @param posX World X position for body placement.
     * @param posY World Y position for body placement.
     * @param posZ World Z position for body placement.
     * @param boxes Pointer to the BoxShapeData array.
     * @param boxCount Number of boxes in the array.
     * @param contentHash Unique hash of the chunk content for shape caching.
     * @param isInitialBuild True if this is the first build (marks as placeholder).
     * @return True if the chunk contains solid geometry, false if air.
     */
    bool SubmitChunkData(int64_t packedPos, float posX, float posY, float posZ,
                         const BoxShapeData* boxes, int boxCount, int contentHash,
                         bool isInitialBuild);

    /**
     * @brief Checks if a chunk is ready for physics.
     *
     * @param packedPos The bit-packed section coordinate.
     * @return True if the chunk is in a ready state.
     */
    bool IsReady(int64_t packedPos) const;

    /**
     * @brief Checks if a chunk is using a placeholder shape.
     *
     * @param packedPos The bit-packed section coordinate.
     * @return True if placeholder or not found.
     */
    bool IsPlaceholder(int64_t packedPos) const;

    /**
     * @brief Checks if a chunk is managed by this system.
     *
     * @param packedPos The bit-packed section coordinate.
     * @return True if the chunk has an entry.
     */
    bool IsManaged(int64_t packedPos) const;

    /**
     * @brief Checks if a body ID belongs to a terrain chunk. O(1) lookup.
     *
     * @param bodyId The Jolt body ID.
     * @return True if the body is a terrain body.
     */
    bool IsTerrainBody(uint32_t bodyId) const;

    std::vector<int64_t> GetActiveChunkPositions() const;

    /**
     * @brief Handles a block update event natively.
     *
     * This method is called from Java when a block in the world changes.
     * it calculates the affected area and wakes up all physics bodies
     * within a 5x5x5 box around the block to ensure correct physics interactions.
     *
     * @param x World X coordinate of the block.
     * @param y World Y coordinate of the block.
     * @param z World Z coordinate of the block.
     */
    void OnBlockUpdate(int x, int y, int z);

    /**
     * @brief Destroys all physics bodies managed by this system.
     *
     * Called during shutdown to ensure no dangling Jolt bodies remain.
     */
    void CleanupAllBodies();

private:
    /**
     * @brief Removes and destroys the physics body for a chunk at a given packed position.
     *
     * Removes the body from the simulation (if added), then destroys it.
     * Updates the state manager to clear the body ID.
     *
     * @param packedPos The bit-packed section coordinate.
     */
    void RemoveBodyAndShape(int64_t packedPos);

    /// The terrain state manager holding all chunk metadata.
    TerrainStateManager m_StateManager;

    /// Pointer to the locking Jolt body interface for thread-safe body operations.
    JPH::BodyInterface* m_BodyInterface;

    /// The Jolt object layer assigned to all terrain bodies.
    uint16_t m_TerrainLayer;
};

} // namespace Velthoric