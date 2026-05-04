/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 *
 * Author: xI-Mx-Ix
 */
#include "Velthoric/Contact/VelthoricContactListener.h"
#include "Velthoric/Terrain/Interaction/TerrainInteraction.h"
#include <Jolt/Physics/PhysicsSystem.h>

namespace Velthoric {

/**
 * @brief Constructs the Velthoric Contact Listener.
 * 
 * @param inPhysicsSystem Reference to the Jolt physics system.
 * @param inWorldRef Global JNI reference to the Java world object.
 */
ContactListener::ContactListener(JPH::PhysicsSystem* inPhysicsSystem, jobject inWorldRef) 
    : m_PhysicsSystem(inPhysicsSystem)
    , m_BodyPairIgnoreHandler(nullptr)
    , m_TerrainHandler(nullptr) {
    (void)inWorldRef;
}

/**
 * @brief Cleans up the listener. 
 * Note: Handlers are owned by the Java side and are NOT deleted here.
 */
ContactListener::~ContactListener() {
    m_BodyPairIgnoreHandler = nullptr;
    m_TerrainHandler = nullptr;
}

/**
 * @brief Sets the body pair ignore handler for collision filtering.
 * @param inHandler Pointer to the native ignore handler.
 */
void ContactListener::SetBodyPairIgnoreHandler(BodyPairIgnoreHandler* inHandler) {
    m_BodyPairIgnoreHandler = inHandler;
}

/**
 * @brief Sets the terrain contact handler for material and interaction logic.
 * @param inHandler Pointer to the native terrain handler.
 */
void ContactListener::SetTerrainContactHandler(TerrainContactHandler* inHandler) {
    m_TerrainHandler = inHandler;
}

/**
 * @brief Validation phase (filtering).
 * 
 * This is the first callback in the contact lifecycle. It delegates to 
 * the BodyPairIgnoreHandler to determine if a collision between specific 
 * body pairs should be suppressed (e.g., spawn protection).
 * 
 * @param inBody1 First body.
 * @param inBody2 Second body.
 * @return RejectContact if the pair is ignored, AcceptAllContacts otherwise.
 */
JPH::ValidateResult ContactListener::OnContactValidate(const JPH::Body& inBody1, const JPH::Body& inBody2, JPH::RVec3Arg, const JPH::CollideShapeResult&) {
    if (m_BodyPairIgnoreHandler && m_BodyPairIgnoreHandler->HasIgnoredPairs()) {
        uint32_t bodyId1 = inBody1.GetID().GetIndexAndSequenceNumber();
        uint32_t bodyId2 = inBody2.GetID().GetIndexAndSequenceNumber();
        if (m_BodyPairIgnoreHandler->ShouldIgnorePair(bodyId1, bodyId2)) {
            return JPH::ValidateResult::RejectContact;
        }
    }
    return JPH::ValidateResult::AcceptAllContactsForThisBodyPair;
}

/**
 * @brief Contact added phase.
 * 
 * Delegates to the TerrainContactHandler to process material properties 
 * and queue terrain interaction events.
 */
void ContactListener::OnContactAdded(const JPH::Body &inBody1, const JPH::Body &inBody2, const JPH::ContactManifold &inManifold, JPH::ContactSettings &ioSettings) {
    if (m_TerrainHandler) {
        m_TerrainHandler->OnContactAdded(inBody1, inBody2, inManifold, ioSettings);
    }
}

/**
 * @brief Contact persisted phase (staying in contact).
 * 
 * Uses the TerrainContactHandler's optimized sharded cache to handle 
 * material properties and interaction triggers with minimal overhead.
 */
void ContactListener::OnContactPersisted(const JPH::Body &inBody1, const JPH::Body &inBody2, const JPH::ContactManifold &inManifold, JPH::ContactSettings &ioSettings) {
    if (m_TerrainHandler) {
        m_TerrainHandler->OnContactPersisted(inBody1, inBody2, inManifold, ioSettings);
    }
}

/**
 * @brief Contact removed phase.
 * 
 * Cleans up metadata associated with the contact in the TerrainContactHandler.
 */
void ContactListener::OnContactRemoved(const JPH::SubShapeIDPair &inSubShapePair) {
    if (m_TerrainHandler) {
        m_TerrainHandler->OnContactRemoved(inSubShapePair);
    }
}

} // namespace Velthoric

/** JNI Bridge Implementation */

extern "C" {

/**
 * @brief JNI Bridge: Instantiates and attaches the VelthoricContactListener dispatcher.
 * 
 * @param env JNI Environment.
 * @param clazz Java class.
 * @param physicsSystemPtr Address of the native Jolt PhysicsSystem.
 * @param world Java world reference.
 * @return Address of the new listener dispatcher.
 */
JNIEXPORT jlong JNICALL
Java_net_xmx_velthoric_jni_VelthoricContactListener_nAttachVelthoricContactListener(JNIEnv *env, jclass clazz, jlong physicsSystemPtr, jobject world) {
    (void)clazz;
    JPH::PhysicsSystem* ps = reinterpret_cast<JPH::PhysicsSystem*>(physicsSystemPtr);
    if (!ps) return 0;

    auto* listener = new Velthoric::ContactListener(ps, world);
    
    // Initialize interaction JNI cache (function IDs)
    Velthoric::TerrainInteraction::InitJNI(env);

    ps->SetContactListener(listener);
    return reinterpret_cast<jlong>(listener);
}

/**
 * @brief JNI Bridge: Injects the BodyPairIgnoreHandler into the dispatcher.
 */
JNIEXPORT void JNICALL
Java_net_xmx_velthoric_jni_VelthoricContactListener_nSetBodyPairIgnoreHandler(JNIEnv *env, jclass clazz, jlong listenerPtr, jlong handlerPtr) {
    (void)env; (void)clazz;
    auto* listener = reinterpret_cast<Velthoric::ContactListener*>(listenerPtr);
    auto* handler = reinterpret_cast<Velthoric::BodyPairIgnoreHandler*>(handlerPtr);
    if (listener) {
        listener->SetBodyPairIgnoreHandler(handler);
    }
}

/**
 * @brief JNI Bridge: Injects the TerrainContactHandler into the dispatcher.
 */
JNIEXPORT jlong JNICALL
Java_net_xmx_velthoric_jni_VelthoricContactListener_nSetTerrainContactHandler(JNIEnv *env, jclass clazz, jlong listenerPtr, jlong handlerPtr) {
    (void)env; (void)clazz;
    auto* listener = reinterpret_cast<Velthoric::ContactListener*>(listenerPtr);
    auto* handler = reinterpret_cast<Velthoric::TerrainContactHandler*>(handlerPtr);
    if (listener) {
        listener->SetTerrainContactHandler(handler);
    }
    return 0; // Return value not used but signature might expect jlong based on previous state
}

/**
 * @brief JNI Bridge: Detaches and destroys the dispatcher.
 */
JNIEXPORT void JNICALL
Java_net_xmx_velthoric_jni_VelthoricContactListener_nDetachVelthoricContactListener(JNIEnv *env, jclass clazz, jlong physicsSystemPtr, jlong listenerPtr) {
    (void)env; (void)clazz;
    JPH::PhysicsSystem* ps = reinterpret_cast<JPH::PhysicsSystem*>(physicsSystemPtr);
    if (ps) {
        ps->SetContactListener(nullptr); 
    }
    auto* listener = reinterpret_cast<Velthoric::ContactListener*>(listenerPtr);
    delete listener;
}

}