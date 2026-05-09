/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 *
 * Author: xI-Mx-Ix
 */
#include "Velthoric/Terrain/State/TerrainStateManager.h"
#include <mutex>

namespace Velthoric {

/**
 * @brief Constructs the state manager and pre-allocates storage.
 *
 * Reserves capacity for 4096 chunk entries to avoid early reallocations
 * during world loading.
 */
TerrainStateManager::TerrainStateManager() {
    m_Entries.reserve(4096);
    m_PackedPosToIndex.reserve(4096);
    m_TerrainBodyIds.reserve(4096);
}

/**
 * @brief Finds the internal index for a given packed position.
 *
 * Caller must hold at least a shared lock on m_Mutex.
 *
 * @param packedPos The bit-packed section coordinate.
 * @return The internal index, or -1 if the position is not managed.
 */
int TerrainStateManager::FindIndex(int64_t packedPos) const {
    auto it = m_PackedPosToIndex.find(packedPos);
    return (it != m_PackedPosToIndex.end()) ? it->second : -1;
}

/**
 * @brief Registers a chunk and increments its reference count.
 *
 * On first allocation, takes an exclusive lock to create a new entry.
 * Subsequent calls only increment the atomic reference count under a shared lock.
 *
 * @param packedPos The bit-packed section coordinate.
 * @return True if this is the first reference (refcount went from 0 to 1).
 */
bool TerrainStateManager::RequestChunk(int64_t packedPos) {
    std::unique_lock<std::shared_mutex> writeLock(m_Mutex);

    int idx = FindIndex(packedPos);
    if (idx != -1) {
        int prev = m_Entries[idx].refCount++;
        return prev == 0;
    }

    // Allocate a new index
    int newIndex;
    if (!m_FreeIndices.empty()) {
        newIndex = m_FreeIndices.front();
        m_FreeIndices.pop_front();
        m_Entries[newIndex] = ChunkEntry{};
    } else {
        newIndex = static_cast<int>(m_Entries.size());
        m_Entries.emplace_back();
    }

    ChunkEntry& entry = m_Entries[newIndex];
    entry.packedPos = packedPos;
    entry.state = STATE_PENDING_DATA;
    entry.bodyId = UNUSED_BODY_ID;
    entry.refCount = 1;
    entry.isPlaceholder = true;

    m_PackedPosToIndex[packedPos] = newIndex;
    return true;
}

/**
 * @brief Decrements the reference count for a chunk.
 *
 * @param packedPos The bit-packed section coordinate.
 * @return True if the reference count reached zero.
 */
bool TerrainStateManager::ReleaseChunk(int64_t packedPos) {
    std::unique_lock<std::shared_mutex> writeLock(m_Mutex);
    int idx = FindIndex(packedPos);
    if (idx == -1) return false;

    int newCount = --m_Entries[idx].refCount;
    return newCount <= 0;
}

/**
 * @brief Removes a chunk entry entirely, recycling its index for reuse.
 *
 * Also removes the body ID from the terrain body lookup set.
 * Should only be called after the physics body has already been destroyed.
 *
 * @param packedPos The bit-packed section coordinate.
 */
void TerrainStateManager::RemoveChunk(int64_t packedPos) {
    std::unique_lock<std::shared_mutex> writeLock(m_Mutex);
    auto it = m_PackedPosToIndex.find(packedPos);
    if (it == m_PackedPosToIndex.end()) return;

    int idx = it->second;

    // Remove body ID from the lookup set
    uint32_t bodyId = m_Entries[idx].bodyId;
    if (bodyId != UNUSED_BODY_ID) {
        m_TerrainBodyIds.erase(bodyId);
    }

    // Reset the entry and recycle the index
    m_Entries[idx] = ChunkEntry{};
    m_FreeIndices.push_back(idx);
    m_PackedPosToIndex.erase(it);
}

/**
 * @brief Returns the current state of a chunk.
 *
 * @param packedPos The bit-packed section coordinate.
 * @return The state constant, or STATE_UNLOADED if not found.
 */
int TerrainStateManager::GetState(int64_t packedPos) const {
    std::shared_lock<std::shared_mutex> readLock(m_Mutex);
    int idx = FindIndex(packedPos);
    return (idx != -1) ? m_Entries[idx].state : STATE_UNLOADED;
}

/**
 * @brief Sets the state of a chunk.
 *
 * @param packedPos The bit-packed section coordinate.
 * @param state The new state constant.
 */
void TerrainStateManager::SetState(int64_t packedPos, int state) {
    std::unique_lock<std::shared_mutex> writeLock(m_Mutex);
    int idx = FindIndex(packedPos);
    if (idx != -1) {
        m_Entries[idx].state = state;
    }
}

/**
 * @brief Returns the Jolt body ID assigned to a chunk.
 *
 * @param packedPos The bit-packed section coordinate.
 * @return The body ID, or UNUSED_BODY_ID if not found or unassigned.
 */
uint32_t TerrainStateManager::GetBodyId(int64_t packedPos) const {
    std::shared_lock<std::shared_mutex> readLock(m_Mutex);
    int idx = FindIndex(packedPos);
    return (idx != -1) ? m_Entries[idx].bodyId : UNUSED_BODY_ID;
}

/**
 * @brief Assigns a Jolt body ID to a chunk and updates the body lookup set.
 *
 * If the chunk previously had a different body ID, the old one is removed
 * from the lookup set before adding the new one.
 *
 * @param packedPos The bit-packed section coordinate.
 * @param bodyId The new body ID, or UNUSED_BODY_ID to clear.
 */
void TerrainStateManager::SetBodyId(int64_t packedPos, uint32_t bodyId) {
    std::unique_lock<std::shared_mutex> writeLock(m_Mutex);
    int idx = FindIndex(packedPos);
    if (idx == -1) return;

    uint32_t oldId = m_Entries[idx].bodyId;
    if (oldId != UNUSED_BODY_ID) {
        m_TerrainBodyIds.erase(oldId);
    }

    m_Entries[idx].bodyId = bodyId;
    if (bodyId != UNUSED_BODY_ID) {
        m_TerrainBodyIds.insert(bodyId);
    }
}

/**
 * @brief Checks if a chunk is using a placeholder shape.
 *
 * @param packedPos The bit-packed section coordinate.
 * @return True if placeholder or not found.
 */
bool TerrainStateManager::IsPlaceholder(int64_t packedPos) const {
    std::shared_lock<std::shared_mutex> readLock(m_Mutex);
    int idx = FindIndex(packedPos);
    return (idx == -1) || m_Entries[idx].isPlaceholder;
}

/**
 * @brief Sets the placeholder flag for a chunk.
 *
 * @param packedPos The bit-packed section coordinate.
 * @param placeholder True to mark as placeholder.
 */
void TerrainStateManager::SetPlaceholder(int64_t packedPos, bool placeholder) {
    std::unique_lock<std::shared_mutex> writeLock(m_Mutex);
    int idx = FindIndex(packedPos);
    if (idx != -1) {
        m_Entries[idx].isPlaceholder = placeholder;
    }
}

/**
 * @brief Checks if a chunk has an entry in the state manager.
 *
 * @param packedPos The bit-packed section coordinate.
 * @return True if managed.
 */
bool TerrainStateManager::IsManaged(int64_t packedPos) const {
    std::shared_lock<std::shared_mutex> readLock(m_Mutex);
    return FindIndex(packedPos) != -1;
}

/**
 * @brief Checks if a chunk is in a ready state.
 *
 * A chunk is considered ready if it is active, inactive, or confirmed air.
 *
 * @param packedPos The bit-packed section coordinate.
 * @return True if the chunk is ready for physics simulation.
 */
bool TerrainStateManager::IsReady(int64_t packedPos) const {
    std::shared_lock<std::shared_mutex> readLock(m_Mutex);
    int idx = FindIndex(packedPos);
    if (idx == -1) return false;
    int s = m_Entries[idx].state;
    return s == STATE_READY_ACTIVE || s == STATE_READY_INACTIVE || s == STATE_AIR_CHUNK;
}

/**
 * @brief Checks if a body ID belongs to a terrain chunk using O(1) hash lookup.
 *
 * This method is called at extremely high frequency from the contact listener
 * during physics ticks with thousands of active bodies.
 *
 * @param bodyId The Jolt body ID to check.
 * @return True if the body is a terrain body.
 */
bool TerrainStateManager::IsTerrainBody(uint32_t bodyId) const {
    if (bodyId == UNUSED_BODY_ID) return false;
    std::shared_lock<std::shared_mutex> readLock(m_Mutex);
    return m_TerrainBodyIds.count(bodyId) > 0;
}

/**
 * @brief Checks if a chunk is currently active in the simulation.
 *
 * @param packedPos The bit-packed section coordinate.
 * @return True if state is STATE_READY_ACTIVE.
 */
bool TerrainStateManager::IsChunkActive(int64_t packedPos) const {
    std::shared_lock<std::shared_mutex> readLock(m_Mutex);
    int idx = FindIndex(packedPos);
    return (idx != -1) && (m_Entries[idx].state == STATE_READY_ACTIVE);
}

/**
 * @brief Returns packed positions of all chunks currently in the READY_ACTIVE state.
 *
 * Pre-allocates the output vector to avoid repeated resizing during iteration.
 *
 * @return A vector of bit-packed section coordinates.
 */
std::vector<int64_t> TerrainStateManager::GetActiveChunkPositions() const {
    std::shared_lock<std::shared_mutex> readLock(m_Mutex);
    std::vector<int64_t> result;
    result.reserve(m_PackedPosToIndex.size() / 4); // Heuristic: ~25% are typically active

    for (const auto& entry : m_Entries) {
        if (entry.state == STATE_READY_ACTIVE) {
            result.push_back(entry.packedPos);
        }
    }
    return result;
}

/**
 * @brief Collects all managed positions and their body IDs for bulk cleanup.
 *
 * Used during shutdown to destroy all terrain bodies.
 *
 * @param outPositions Output vector receiving all managed packed positions.
 * @param outBodyIds Output vector receiving corresponding body IDs.
 */
void TerrainStateManager::GetAllManagedBodies(std::vector<int64_t>& outPositions, std::vector<uint32_t>& outBodyIds) const {
    std::shared_lock<std::shared_mutex> readLock(m_Mutex);
    outPositions.reserve(m_PackedPosToIndex.size());
    outBodyIds.reserve(m_PackedPosToIndex.size());

    for (const auto& [pos, idx] : m_PackedPosToIndex) {
        outPositions.push_back(pos);
        outBodyIds.push_back(m_Entries[idx].bodyId);
    }
}

/**
 * @brief Clears all state, body lookups, and recycled indices.
 */
void TerrainStateManager::Clear() {
    std::unique_lock<std::shared_mutex> writeLock(m_Mutex);
    m_PackedPosToIndex.clear();
    m_Entries.clear();
    m_FreeIndices.clear();
    m_TerrainBodyIds.clear();
}

} // namespace Velthoric