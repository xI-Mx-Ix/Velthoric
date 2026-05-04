/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 *
 * Author xI-Mx-Ix
 */
#pragma once
#include <Jolt/Jolt.h>
#include <mutex>
#include <unordered_set>
#include <vector>
#include <cstdint>
#include <atomic>

namespace Velthoric {

/**
 * @brief High-performance handler for ignoring collisions between specific body pairs.
 * 
 * This class provides a thread-safe mechanism to ignore collisions between individual
 * body pairs in Jolt Physics. Unlike collision groups, this allows fine-grained control
 * without the limitations of group-based filtering, making it ideal for scenarios like
 * grabbing mechanics where bodies may be nested inside grabbers.
 * 
 * The implementation uses std::unordered_set for O(1) lookup performance and normalizes
 * pairs (smaller ID first) to ensure that (a, b) and (b, a) are treated identically.
 * 
 * Thread-safety is ensured via a mutex, making it safe to call from multiple threads
 * (e.g., from the contact listener and from game logic).
 */
class BodyPairIgnoreHandler {
public:
    /**
     * @brief Constructs a new BodyPairIgnoreHandler.
     */
    BodyPairIgnoreHandler();

    /**
     * @brief Destructor.
     */
    ~BodyPairIgnoreHandler();

    /**
     * @brief Adds a body pair to the ignore list.
     * 
     * The pair is stored in normalized order (smaller ID first).
     * 
     * @param bodyId1 The first body ID.
     * @param bodyId2 The second body ID.
     */
    void IgnorePair(uint32_t bodyId1, uint32_t bodyId2);

    /**
     * @brief Removes a body pair from the ignore list.
     * 
     * @param bodyId1 The first body ID.
     * @param bodyId2 The second body ID.
     */
    void RemoveIgnorePair(uint32_t bodyId1, uint32_t bodyId2);

    /**
     * @brief Checks if a body pair is currently being ignored.
     * 
     * @param bodyId1 The first body ID.
     * @param bodyId2 The second body ID.
     * @return true if the pair is ignored, false otherwise.
     */
    bool IsPairIgnored(uint32_t bodyId1, uint32_t bodyId2) const;

    /**
     * @brief Returns all currently ignored body pairs.
     * 
     * @param outPairs Output vector to receive the pairs. Each pair is stored as
     *                two consecutive uint32_t values: [bodyId1, bodyId2].
     */
    void GetIgnoredPairs(std::vector<uint32_t>& outPairs) const;

    /**
     * @brief Callback when a body is removed from the physics system.
     * 
     * Automatically removes all ignored pairs involving the specified body ID.
     * 
     * @param bodyId The body ID that was removed.
     */
    void OnBodyRemoved(uint32_t bodyId);

    /**
     * @brief Clears all ignored pairs.
     */
    void Clear();

    /**
     * @brief Returns the number of currently ignored pairs.
     * 
     * @return The count of ignored pairs.
     */
    size_t Size() const;

    /**
     * @brief Returns whether the handler has any ignored pairs.
     *
     * This is a fast check that can be used to skip contact validation
     * when no pairs are being ignored.
     *
     * @return true if there are ignored pairs, false otherwise.
     */
    bool HasIgnoredPairs() const;

    /**
     * @brief Checks if a body is involved in any ignored pairs.
     *
     * This is a fast O(1) check that can be used to skip contact validation
     * for bodies that are not involved in any ignored pairs at all.
     *
     * @param bodyId The body ID to check.
     * @return true if the body is involved in ignored pairs, false otherwise.
     */
    bool IsBodyInvolved(uint32_t bodyId) const;

    /**
     * @brief Optimized single-call check for contact validation.
     *
     * This combines all checks (HasIgnoredPairs, IsBodyInvolved, IsPairIgnored)
     * into a single mutex lock for maximum performance. This is the preferred
     * method to call from the contact listener.
     *
     * @param bodyId1 The first body ID.
     * @param bodyId2 The second body ID.
     * @return true if the pair should be ignored, false otherwise.
     */
    bool ShouldIgnorePair(uint32_t bodyId1, uint32_t bodyId2) const;

private:
    /**
     * @brief Computes a normalized key for a body pair.
     *
     * The key is computed such that (a, b) and (b, a) produce the same key.
     * The smaller ID is placed in the lower 32 bits, the larger in the upper 32 bits.
     *
     * @param bodyId1 The first body ID.
     * @param bodyId2 The second body ID.
     * @return A 64-bit key representing the normalized pair.
     */
    static uint64_t MakePairKey(uint32_t bodyId1, uint32_t bodyId2);

    /// Mutex for thread-safe access to the ignored pairs sets.
    mutable std::mutex m_Mutex;

    /**
     * @brief Atomic flag indicating whether there are any ignored pairs.
     * 
     * This allows a lock-free fast path in ShouldIgnorePair when no pairs are 
     * currently being ignored in the entire handler.
     */
    std::atomic<bool> m_HasIgnoredPairs;

    /**
     * @brief Set of ignored body pairs, stored as normalized 64-bit keys.
     * 
     * Keys are generated by MakePairKey from two 32-bit body IDs.
     */
    std::unordered_set<uint64_t> m_IgnoredPairs;

    /**
     * @brief Set of all body IDs that are involved in at least one ignored pair.
     * 
     * This allows a fast O(1) check to skip full pair lookup for bodies that 
     * are not part of any filtered collision.
     */
    std::unordered_set<uint32_t> m_InvolvedBodies;
};

} // namespace Velthoric