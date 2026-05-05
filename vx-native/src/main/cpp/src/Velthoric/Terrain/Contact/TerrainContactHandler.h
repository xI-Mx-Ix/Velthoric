/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 *
 * Author: xI-Mx-Ix
 */
#pragma once
#include <Jolt/Jolt.h>
#include <Jolt/Physics/Body/Body.h>
#include <Jolt/Physics/Collision/ContactListener.h>
#include <atomic>
#include <array>
#include <jni.h>

JPH_NAMESPACE_BEGIN
class PhysicsSystem;
JPH_NAMESPACE_END

namespace Velthoric {

/**
 * @brief Specialized handler for terrain-specific collision logic and interactions.
 * 
 * This handler manages:
 * 1. Material Overrides: Combines friction and restitution specifically for terrain shapes.
 * 2. High-Performance Caching: Uses a sharded flat-cache with spinlocks to avoid expensive
 *    Jolt shape traversals during persisting contacts.
 * 3. Interaction Pipeline: Triggers events for TerrainInteraction (breaking, particles).
 */
class TerrainContactHandler {
public:
    /**
     * @brief Constructs the Terrain Contact Handler.
     * 
     * @param inPhysicsSystem Pointer to the Jolt physics system.
     * @param inWorldRef Global JNI reference to the Java VxPhysicsWorld object.
     */
    TerrainContactHandler(JPH::PhysicsSystem* inPhysicsSystem, jobject inWorldRef);

    /**
     * @brief Processes a new terrain contact.
     * Extracts material data and populates the flat cache.
     */
    void OnContactAdded(const JPH::Body &inBody1, const JPH::Body &inBody2, const JPH::ContactManifold &inManifold, JPH::ContactSettings &ioSettings);

    /**
     * @brief Processes an existing terrain contact using the fast path.
     * Retrieves combined friction/restitution from the flat cache.
     */
    void OnContactPersisted(const JPH::Body &inBody1, const JPH::Body &inBody2, const JPH::ContactManifold &inManifold, JPH::ContactSettings &ioSettings);

    /**
     * @brief Removes contact metadata from the sharded cache.
     */
    void OnContactRemoved(const JPH::SubShapeIDPair &inSubShapePair);

private:
    /// Pointer to the physics system for interaction context.
    JPH::PhysicsSystem* m_PhysicsSystem;
    
    /// Global JNI reference to the world object for interaction callbacks.
    jobject m_WorldRef;

    /**
     * @brief A single entry in the sharded flat cache.
     */
    struct CachedContact {
        uint64_t key;       ///< Unique key composed of body IDs and sub-shape IDs.
        float friction;    ///< Pre-calculated combined friction coefficient.
        float restitution; ///< Pre-calculated combined restitution (bounciness).
        uint32_t matId;    ///< Cached material ID to prevent slow sub-shape lookups.
    };

    /// Entries per shard. 64 allows for efficient linear search within a cache line.
    static constexpr int ENTRIES_PER_SHARD = 64;

    /**
     * @brief A sharded cache segment protected by an atomic spinlock.
     * Aligned to 64 bytes to prevent false sharing across CPU cores.
     */
    struct alignas(64) CacheShard {
        std::atomic_flag lock = ATOMIC_FLAG_INIT; ///< Fast atomic spinlock.
        std::array<CachedContact, ENTRIES_PER_SHARD> entries = {}; ///< Flat entry storage.
        uint32_t nextInsertIdx = 0; ///< FIFO replacement index.

        inline void Lock() { while (lock.test_and_set(std::memory_order_acquire)); }
        inline void Unlock() { lock.clear(std::memory_order_release); }
    };

    /// Total number of shards to minimize lock contention.
    static constexpr int NUM_SHARDS = 128;
    
    /// The sharded flat cache storage.
    CacheShard m_CacheShards[NUM_SHARDS];

    /**
     * @brief Generates a unique 64-bit hash key for a contact pair.
     * 
     * @param b1 Body ID 1.
     * @param b2 Body ID 2.
     * @param s1 Sub-shape ID 1.
     * @param s2 Sub-shape ID 2.
     * @return uint64_t Normalized hash key.
     */
    static inline uint64_t MakeContactKey(uint32_t b1, uint32_t b2, uint32_t s1, uint32_t s2) {
        uint64_t h = (static_cast<uint64_t>(b1) << 32) | b2;
        h ^= static_cast<uint64_t>(s1) * 0x9E3779B97F4A7C15ULL;
        h ^= static_cast<uint64_t>(s2) * 0xBF58476D1CE4E5B9ULL;
        return h;
    }
};

} // namespace Velthoric