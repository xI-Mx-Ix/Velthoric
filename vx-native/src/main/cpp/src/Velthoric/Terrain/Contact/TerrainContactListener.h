/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 *
 * Author xI-Mx-Ix
 */
#pragma once
#include <Jolt/Jolt.h>
#include <Jolt/Physics/Collision/ContactListener.h>
#include <mutex>
#include <unordered_map>
#include <jni.h>
#include "../../BodyPairIgnoreManager/BodyPairIgnoreManager.h"

JPH_NAMESPACE_BEGIN
class PhysicsSystem;
JPH_NAMESPACE_END

namespace Velthoric {

/**
 * Native contact listener for Velthoric.
 * 
 * Handles the extraction of custom physical properties (friction, restitution)
 * from Velthoric::TerrainMaterial during collisions. This ensures that 
 * different block types (Ice, Slime, etc.) behave correctly in the physics world.
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

    virtual ~ContactListener() override;

    /**
     * @brief Sets the body pair ignore manager.
     * 
     * @param inManager Pointer to the ignore manager (can be null).
     */
    void SetBodyPairIgnoreManager(BodyPairIgnoreManager* inManager);

    /**
     * @brief Gets the body pair ignore manager.
     * 
     * @return Pointer to the ignore manager (may be null).
     */
    BodyPairIgnoreManager* GetBodyPairIgnoreManager() const;

    /** @brief Broadphase validation filter. */
    virtual JPH::ValidateResult OnContactValidate(const JPH::Body &inBody1, const JPH::Body &inBody2, JPH::RVec3Arg inBaseOffset, const JPH::CollideShapeResult &inCollisionResult) override;

    /** @brief Handles the creation of a new contact and caches material properties. */
    virtual void OnContactAdded(const JPH::Body &inBody1, const JPH::Body &inBody2, const JPH::ContactManifold &inManifold, JPH::ContactSettings &ioSettings) override;

    /** @brief Updates existing contacts using cached material properties for performance. */
    virtual void OnContactPersisted(const JPH::Body &inBody1, const JPH::Body &inBody2, const JPH::ContactManifold &inManifold, JPH::ContactSettings &ioSettings) override;

    /** @brief Evicts cached material properties when a contact is destroyed. */
    virtual void OnContactRemoved(const JPH::SubShapeIDPair &inSubShapePair) override;

private:
    JPH::PhysicsSystem* m_PhysicsSystem;
    jobject m_WorldRef;
    BodyPairIgnoreManager* m_BodyPairIgnoreManager;

    /// Cached material properties for a contact pair, avoiding repeated BVH lookups.
    struct CachedContact {
        float friction;
        float restitution;
    };

    /// Thread-safe cache keyed on SubShapeIDPair hash.
    /// Sharded to eliminate lock contention during massive impacts.
    static constexpr int NUM_SHARDS = 16;
    struct CacheShard {
        std::mutex mutex;
        std::unordered_map<uint64_t, CachedContact> entries;
    };
    CacheShard m_CacheShards[NUM_SHARDS];

    /// Computes a unique key for the contact cache from body and sub-shape IDs.
    static uint64_t MakeContactKey(uint32_t bodyId1, uint32_t bodyId2, uint32_t subShape1, uint32_t subShape2);
};

} // namespace Velthoric