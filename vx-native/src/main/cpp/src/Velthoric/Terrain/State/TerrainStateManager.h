/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 *
 * Author: xI-Mx-Ix
 */
#pragma once
#include <Jolt/Jolt.h>
#include <cstdint>
#include <vector>
#include <deque>
#include <unordered_map>
#include <unordered_set>
#include <shared_mutex>
#include <atomic>

namespace Velthoric {

/**
 * @brief High-performance, thread-safe state manager for terrain chunk physics data.
 *
 * Manages the lifecycle state, body IDs, reference counts, and placeholder flags
 * for all terrain chunks. Uses a Structure-of-Arrays (SoA) layout with a
 * shared_mutex for read-heavy concurrent access patterns typical in physics
 * simulations with thousands of bodies.
 *
 * Structural changes (add/remove) take an exclusive lock.
 * Individual field reads use a shared lock for maximum read concurrency.
 * Body ID lookups use a dedicated hash set for O(1) terrain body identification.
 */
class TerrainStateManager {
public:
    /// Chunk has no entry or was fully cleaned up.
    static constexpr int STATE_UNLOADED = 0;

    /// Chunk data has been requested from Java but not yet received.
    static constexpr int STATE_PENDING_DATA = 1;

    /// Chunk has a physics body but is not added to the simulation.
    static constexpr int STATE_READY_INACTIVE = 2;

    /// Chunk has a physics body and is actively participating in the simulation.
    static constexpr int STATE_READY_ACTIVE = 3;

    /// Chunk is being removed and should not be modified.
    static constexpr int STATE_REMOVING = 4;

    /// Chunk was confirmed to contain only air blocks and needs no physics body.
    static constexpr int STATE_AIR_CHUNK = 5;

    /// Sentinel value for an unassigned or invalid Jolt body ID.
    static constexpr uint32_t UNUSED_BODY_ID = 0;

    /**
     * @brief Constructs the state manager with default initial capacity.
     */
    TerrainStateManager();



    /**
     * @brief Registers a chunk and increments its reference count.
     *
     * If the chunk does not yet have an entry, a new one is allocated.
     * Thread-safe. Takes an exclusive lock only on first allocation.
     *
     * @param packedPos The bit-packed section coordinate.
     * @return True if this is the first reference (refcount went from 0 to 1).
     */
    bool RequestChunk(int64_t packedPos);

    /**
     * @brief Decrements the reference count for a chunk.
     *
     * @param packedPos The bit-packed section coordinate.
     * @return True if the reference count reached zero and the chunk should be unloaded.
     */
    bool ReleaseChunk(int64_t packedPos);

    /**
     * @brief Removes a chunk entry entirely, recycling its index.
     *
     * Should only be called after the physics body has been destroyed.
     *
     * @param packedPos The bit-packed section coordinate.
     */
    void RemoveChunk(int64_t packedPos);



    /**
     * @brief Returns the current state of a chunk.
     *
     * @param packedPos The bit-packed section coordinate.
     * @return The state constant, or STATE_UNLOADED if not found.
     */
    int GetState(int64_t packedPos) const;

    /**
     * @brief Sets the state of a chunk.
     *
     * @param packedPos The bit-packed section coordinate.
     * @param state The new state constant.
     */
    void SetState(int64_t packedPos, int state);



    /**
     * @brief Returns the Jolt body ID for a chunk.
     *
     * @param packedPos The bit-packed section coordinate.
     * @return The body ID, or UNUSED_BODY_ID if not found or unassigned.
     */
    uint32_t GetBodyId(int64_t packedPos) const;

    /**
     * @brief Assigns a Jolt body ID to a chunk and updates the body lookup set.
     *
     * @param packedPos The bit-packed section coordinate.
     * @param bodyId The Jolt body ID to assign, or UNUSED_BODY_ID to clear.
     */
    void SetBodyId(int64_t packedPos, uint32_t bodyId);



    /**
     * @brief Checks if a chunk is using a placeholder (initial) shape.
     *
     * @param packedPos The bit-packed section coordinate.
     * @return True if the chunk is a placeholder or not found.
     */
    bool IsPlaceholder(int64_t packedPos) const;

    /**
     * @brief Sets the placeholder flag for a chunk.
     *
     * @param packedPos The bit-packed section coordinate.
     * @param placeholder True to mark as placeholder, false for finalized.
     */
    void SetPlaceholder(int64_t packedPos, bool placeholder);



    /**
     * @brief Checks if a chunk has an active entry in the state manager.
     *
     * @param packedPos The bit-packed section coordinate.
     * @return True if the chunk is managed.
     */
    bool IsManaged(int64_t packedPos) const;

    /**
     * @brief Checks if a chunk is in a ready state (active, inactive, or air).
     *
     * @param packedPos The bit-packed section coordinate.
     * @return True if the chunk is ready for physics.
     */
    bool IsReady(int64_t packedPos) const;

    /**
     * @brief Checks if a given body ID belongs to a terrain chunk. O(1) lookup.
     *
     * @param bodyId The Jolt body ID to check.
     * @return True if the body is a terrain body.
     */
    bool IsTerrainBody(uint32_t bodyId) const;

    /**
     * @brief Checks if a chunk is currently active in the physics simulation.
     *
     * @param packedPos The bit-packed section coordinate.
     * @return True if the chunk state is STATE_READY_ACTIVE.
     */
    bool IsChunkActive(int64_t packedPos) const;



    /**
     * @brief Returns the packed positions of all chunks in the READY_ACTIVE state.
     *
     * @return A vector of bit-packed section coordinates.
     */
    std::vector<int64_t> GetActiveChunkPositions() const;

    /**
     * @brief Returns all managed packed positions and their body IDs for cleanup.
     *
     * @param outPositions Output vector of packed positions.
     * @param outBodyIds Output vector of corresponding body IDs.
     */
    void GetAllManagedBodies(std::vector<int64_t>& outPositions, std::vector<uint32_t>& outBodyIds) const;

    /**
     * @brief Clears all state and releases all internal storage.
     */
    void Clear();

private:
    /**
     * @brief Internal per-chunk data stored in a flat array for cache efficiency.
     */
    struct ChunkEntry {
        int64_t packedPos = 0;     ///< Bit-packed section coordinate.
        int state = STATE_UNLOADED; ///< Current lifecycle state.
        uint32_t bodyId = UNUSED_BODY_ID; ///< Assigned Jolt body ID.
        int refCount = 0;          ///< Number of active references from the tracker.
        bool isPlaceholder = true; ///< Whether the shape is a placeholder (initial build).
    };

    /**
     * @brief Finds the internal index for a packed position. Caller must hold a lock.
     *
     * @param packedPos The bit-packed section coordinate.
     * @return The index, or -1 if not found.
     */
    int FindIndex(int64_t packedPos) const;

    /// Protects all structural and data access. Shared for reads, exclusive for writes.
    mutable std::shared_mutex m_Mutex;

    /// Maps bit-packed coordinates to internal array indices.
    std::unordered_map<int64_t, int> m_PackedPosToIndex;

    /// Flat array of chunk entries for cache-friendly iteration.
    std::vector<ChunkEntry> m_Entries;

    /// Recycled indices from removed chunks.
    std::deque<int> m_FreeIndices;

    /// Hash set of all active terrain body IDs for O(1) lookups.
    std::unordered_set<uint32_t> m_TerrainBodyIds;
};

} // namespace Velthoric