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
    ContactListener(JPH::PhysicsSystem* inPhysicsSystem, jobject inWorldRef);

    virtual ~ContactListener() override;

    virtual JPH::ValidateResult OnContactValidate(const JPH::Body &inBody1, const JPH::Body &inBody2, JPH::RVec3Arg inBaseOffset, const JPH::CollideShapeResult &inCollisionResult) override;

    virtual void OnContactAdded(const JPH::Body &inBody1, const JPH::Body &inBody2, const JPH::ContactManifold &inManifold, JPH::ContactSettings &ioSettings) override;

    virtual void OnContactPersisted(const JPH::Body &inBody1, const JPH::Body &inBody2, const JPH::ContactManifold &inManifold, JPH::ContactSettings &ioSettings) override;

    virtual void OnContactRemoved(const JPH::SubShapeIDPair &inSubShapePair) override;

private:
    JPH::PhysicsSystem* m_PhysicsSystem;
    jobject m_WorldRef;

    /// Cached material properties for a contact pair, avoiding repeated BVH lookups.
    struct CachedContact {
        float friction;
        float restitution;
    };

    /// Thread-safe cache keyed on SubShapeIDPair hash.
    std::mutex m_CacheMutex;
    std::unordered_map<uint64_t, CachedContact> m_ContactCache;

    /// Computes a unique key for the contact cache from body and sub-shape IDs.
    static uint64_t MakeContactKey(uint32_t bodyId1, uint32_t bodyId2, uint32_t subShape1, uint32_t subShape2);
};

} // namespace Velthoric