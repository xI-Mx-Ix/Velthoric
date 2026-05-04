/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 *
 * Author xI-Mx-Ix
 */
#pragma once
#include <Jolt/Jolt.h>
#include <Jolt/Physics/Collision/ContactListener.h>
#include <jni.h>
#include "../BodyPairIgnore/BodyPairIgnoreHandler.h"
#include "../Terrain/Contact/TerrainContactHandler.h"

JPH_NAMESPACE_BEGIN
class PhysicsSystem;
JPH_NAMESPACE_END

namespace Velthoric {

/**
 * @brief Global Dispatcher for physics contact events in the Velthoric engine.
 * 
 * This class implements the JPH::ContactListener interface and serves as a central
 * hub for all collision events. Instead of processing logic itself, it delegates 
 * specialized tasks to modular handlers:
 * 
 * 1. BodyPairIgnoreHandler: Filters collisions (e.g., spawn protection).
 * 2. TerrainContactHandler: Manages material properties and interactions.
 * 
 * Handlers are injected from the Java side to maintain clear ownership and 
 * lifecycle management within the VxPhysicsWorld.
 */
class ContactListener : public JPH::ContactListener {
public:
    /**
     * @brief Constructs the Velthoric Contact Listener.
     * 
     * @param inPhysicsSystem Pointer to the Jolt physics system.
     * @param inWorldRef Global JNI reference to the Java VxPhysicsWorld object.
     */
    ContactListener(JPH::PhysicsSystem* inPhysicsSystem, jobject inWorldRef);

    /**
     * @brief Securely cleans up the listener dispatcher.
     * Handlers are NOT deleted here as they are owned by the Java side.
     */
    virtual ~ContactListener() override;

    /**
     * @brief Injects the body pair ignore handler.
     * 
     * @param inHandler Pointer to the handler instance.
     */
    void SetBodyPairIgnoreHandler(BodyPairIgnoreHandler* inHandler);

    /**
     * @brief Injects the terrain contact handler.
     * 
     * @param inHandler Pointer to the handler instance.
     */
    void SetTerrainContactHandler(TerrainContactHandler* inHandler);

    /**
     * @brief First phase of collision: filtering.
     * Delegates to BodyPairIgnoreHandler to determine if a contact should be rejected.
     */
    virtual JPH::ValidateResult OnContactValidate(const JPH::Body &inBody1, const JPH::Body &inBody2, JPH::RVec3Arg inBaseOffset, const JPH::CollideShapeResult &inCollisionResult) override;

    /**
     * @brief Triggered when a new contact is established.
     * Delegates to TerrainContactHandler for material combining and interaction queuing.
     */
    virtual void OnContactAdded(const JPH::Body &inBody1, const JPH::Body &inBody2, const JPH::ContactManifold &inManifold, JPH::ContactSettings &ioSettings) override;

    /**
     * @brief Triggered every frame a contact persists.
     * Delegates to TerrainContactHandler using optimized cache lookups.
     */
    virtual void OnContactPersisted(const JPH::Body &inBody1, const JPH::Body &inBody2, const JPH::ContactManifold &inManifold, JPH::ContactSettings &ioSettings) override;

    /**
     * @brief Triggered when a contact is removed.
     * Delegates to TerrainContactHandler to clean up metadata.
     */
    virtual void OnContactRemoved(const JPH::SubShapeIDPair &inSubShapePair) override;

private:
    /// Reference to the physics system.
    JPH::PhysicsSystem* m_PhysicsSystem;
    
    /// Pointer to the injected ignore handler (filtering module).
    BodyPairIgnoreHandler* m_BodyPairIgnoreHandler;
    
    /// Pointer to the injected terrain handler (material/interaction module).
    TerrainContactHandler* m_TerrainHandler;
};

} // namespace Velthoric