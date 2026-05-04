/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 *
 * Author xI-Mx-Ix
 */
#pragma once
#include <Jolt/Jolt.h>
#include <Jolt/Physics/Collision/ContactListener.h>
#include <atomic>
#include <array>
#include <jni.h>
#include "../../BodyPairIgnoreManager/BodyPairIgnoreManager.h"

JPH_NAMESPACE_BEGIN
class PhysicsSystem;
JPH_NAMESPACE_END

namespace Velthoric {

/**
 * @brief High-performance native contact listener for Velthoric.
 * 
 * Optimized for massive-scale simulations (5000+ bodies). This listener handles:
 * - Material-based physical property combination (Friction/Restitution).
 * - Collision filtering via BodyPairIgnoreManager.
 * - Triggering of TerrainInteraction events (breaking, transforming, particles).
 * 
 * Performance Architecture:
 * 1. Sharded Flat Cache: Replaces std::unordered_map with fixed-size arrays to eliminate heap churn.
 * 2. Atomic Spinlocks: Replaces std::mutex with std::atomic_flag to prevent thread context switches.
 * 3. Cache-Line Alignment: Prevents "False Sharing" between CPU cores during multi-threaded physics ticks.
 */
class ContactListener : public JPH::ContactListener {
public:
    /**
     * @brief Constructs a new ContactListener.
     * 
     * @param inPhysicsSystem The Jolt physics system to listen to.
     * @param inWorldRef Global reference to the Java VxPhysicsWorld object.
     */
    ContactListener(JPH::PhysicsSystem* inPhysicsSystem, jobject inWorldRef);

    /**
     * @brief Destructor. Cleans up the Java global reference.
     */
    virtual ~ContactListener() override;

    /**
     * @brief Sets the manager for ignored body pairs.
     * 
     * @param inManager Pointer to the ignore manager (can be null).
     */
    void SetBodyPairIgnoreManager(BodyPairIgnoreManager* inManager);

    /**
     * @brief Gets the current body pair ignore manager.
     * 
     * @return Pointer to the ignore manager (may be null).
     */
    BodyPairIgnoreManager* GetBodyPairIgnoreManager() const;

    /**
     * @brief Broadphase validation filter.
     * Checks the BodyPairIgnoreManager for rejected collisions before expensive manifold calculation.
     */
    virtual JPH::ValidateResult OnContactValidate(const JPH::Body &inBody1, const JPH::Body &inBody2, JPH::RVec3Arg inBaseOffset, const JPH::CollideShapeResult &inCollisionResult) override;

    /**
     * @brief Handles the creation of a new contact.
     * Extracts material properties (e.g., Slime/Ice) and caches them for future frames.
     */
    virtual void OnContactAdded(const JPH::Body &inBody1, const JPH::Body &inBody2, const JPH::ContactManifold &inManifold, JPH::ContactSettings &ioSettings) override;

    /**
     * @brief Updates existing contacts.
     * Uses the high-speed flat cache to re-apply physical properties without BVH lookups.
     */
    virtual void OnContactPersisted(const JPH::Body &inBody1, const JPH::Body &inBody2, const JPH::ContactManifold &inManifold, JPH::ContactSettings &ioSettings) override;

    /**
     * @brief Evicts cached material properties when a contact is destroyed.
     */
    virtual void OnContactRemoved(const JPH::SubShapeIDPair &inSubShapePair) override;

private:
    /// Pointer to the Jolt physics system.
    JPH::PhysicsSystem* m_PhysicsSystem;

    /// Global JNI reference to the Java world object.
    jobject m_WorldRef;

    /// Dependency: Manager for filtering body pairs.
    BodyPairIgnoreManager* m_BodyPairIgnoreManager;

    /**
     * @brief Cached material properties for a specific contact point.
     */
    struct CachedContact {
        uint64_t key;       ///< Combined hash of body IDs and sub-shape IDs.
        float friction;    ///< Computed combined friction.
        float restitution; ///< Computed combined restitution.
    };

    /**
     * @brief A cache shard using a flat array and a spinlock.
     * Aligned to 64 bytes (typical L1 cache line size) to prevent false sharing.
     */
    static constexpr int ENTRIES_PER_SHARD = 64;
    struct alignas(64) CacheShard {
        std::atomic_flag lock = ATOMIC_FLAG_INIT;           ///< Atomic spinlock for ultra-fast locking.
        std::array<CachedContact, ENTRIES_PER_SHARD> entries = {}; ///< Flat storage (linear search is faster for 64 entries).
        uint32_t nextInsertIdx = 0;                         ///< Simple FIFO replacement strategy.

        /// Acquires the spinlock via test-and-set.
        inline void Lock() { while (lock.test_and_set(std::memory_order_acquire)); }
        /// Releases the spinlock.
        inline void Unlock() { lock.clear(std::memory_order_release); }
    };

    /**
     * @brief Number of cache shards.
     * 128 shards virtually eliminate lock contention between physics threads.
     */
    static constexpr int NUM_SHARDS = 128;
    CacheShard m_CacheShards[NUM_SHARDS];

    /**
     * @brief Computes a high-entropy 64-bit key for a contact pair.
     * 
     * @param b1 Body 1 Index.
     * @param b2 Body 2 Index.
     * @param s1 SubShape 1 ID.
     * @param s2 SubShape 2 ID.
     * @return uint64_t The generated cache key.
     */
    static inline uint64_t MakeContactKey(uint32_t b1, uint32_t b2, uint32_t s1, uint32_t s2) {
        uint64_t h = (static_cast<uint64_t>(b1) << 32) | b2;
        h ^= static_cast<uint64_t>(s1) * 0x9E3779B97F4A7C15ULL;
        h ^= static_cast<uint64_t>(s2) * 0xBF58476D1CE4E5B9ULL;
        return h;
    }
};

} // namespace Velthoric