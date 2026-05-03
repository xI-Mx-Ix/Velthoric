/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 *
 * Author xI-Mx-Ix
 */
#include "TerrainContactListener.h"
#include "../../Terrain/TerrainGenerator.h"
#include "../Interaction/TerrainInteraction.h"
#include <Jolt/Physics/Collision/PhysicsMaterial.h>
#include <Jolt/Physics/PhysicsSystem.h>
#include <Jolt/Physics/Body/Body.h>
#include <cstring>
#include <algorithm>
#include <cmath>

/**
 * @brief Handles custom collision material properties (friction, restitution) for terrain in Jolt Physics.
 * 
 * Jolt Physics natively supports Materials per sub-shape, but requires a ContactListener 
 * to intercept collision events and explicitly combine or override properties like bounciness
 * and slide friction. This listener is attached to the PhysicsSystem during initialization
 * and ensures Minecraft blocks (like Slime or Ice) feel physically accurate.
 */

namespace Velthoric {

ContactListener::ContactListener(JPH::PhysicsSystem* inPhysicsSystem, jobject inWorldRef) : m_PhysicsSystem(inPhysicsSystem) {
    JNIEnv* env = nullptr;
    JavaVM* vm = nullptr;
    jint res = JNI_GetCreatedJavaVMs(&vm, 1, nullptr);
    if (res == JNI_OK && vm->GetEnv((void**)&env, JNI_VERSION_1_8) == JNI_OK) {
        m_WorldRef = env->NewGlobalRef(inWorldRef);
    } else {
        m_WorldRef = nullptr;
    }
}

ContactListener::~ContactListener() {
    if (m_WorldRef) {
        JNIEnv* env = nullptr;
        JavaVM* vm = nullptr;
        jint res = JNI_GetCreatedJavaVMs(&vm, 1, nullptr);
        if (res == JNI_OK && vm->GetEnv((void**)&env, JNI_VERSION_1_8) == JNI_OK) {
            env->DeleteGlobalRef(m_WorldRef);
        }
    }
}

/**
 * @brief Broadphase validation filter. Accepts all contacts.
 */
JPH::ValidateResult ContactListener::OnContactValidate(const JPH::Body &inBody1, const JPH::Body &inBody2, JPH::RVec3Arg inBaseOffset, const JPH::CollideShapeResult &inCollisionResult) {
    return JPH::ValidateResult::AcceptAllContactsForThisBodyPair;
}

/**
 * @brief Computes a 64-bit cache key from a SubShapeIDPair.
 * 
 * Combines both BodyIDs and SubShapeIDs into a single hash for O(1) cache lookups.
 */
uint64_t ContactListener::MakeContactKey(uint32_t bodyId1, uint32_t bodyId2, uint32_t subShape1, uint32_t subShape2) {
    uint64_t h = static_cast<uint64_t>(bodyId1);
    h = (h << 32) | static_cast<uint64_t>(bodyId2);
    h ^= static_cast<uint64_t>(subShape1) * 2654435761ULL;
    h ^= static_cast<uint64_t>(subShape2) * 40503ULL;
    return h;
}

/**
 * @brief Called once when a new contact is created.
 * 
 * Performs the full (expensive) material lookup via GetMaterial() and caches
 * the computed friction/restitution for use in OnContactPersisted.
 */
void ContactListener::OnContactAdded(const JPH::Body &inBody1, const JPH::Body &inBody2, const JPH::ContactManifold &inManifold, JPH::ContactSettings &ioSettings) {
    // Early-out: skip entirely if neither body is terrain
    if (inBody1.GetObjectLayer() != static_cast<JPH::ObjectLayer>(2) && inBody2.GetObjectLayer() != static_cast<JPH::ObjectLayer>(2)) {
        return;
    }

    // Full material extraction (expensive BVH traversal)
    const JPH::PhysicsMaterial* mat1 = inBody1.GetShape()->GetMaterial(inManifold.mSubShapeID1);
    const JPH::PhysicsMaterial* mat2 = inBody2.GetShape()->GetMaterial(inManifold.mSubShapeID2);

    float friction1 = inBody1.GetFriction();
    float restitution1 = inBody1.GetRestitution();
    float friction2 = inBody2.GetFriction();
    float restitution2 = inBody2.GetRestitution();

    bool foundCustom = false;

    if (mat1 != nullptr && mat1->GetDebugName() == TerrainMaterial::sTerrainMaterialName) {
        const TerrainMaterial* tMat1 = static_cast<const TerrainMaterial*>(mat1);
        friction1 = tMat1->mFriction;
        restitution1 = tMat1->mRestitution;
        foundCustom = true;
    }

    if (mat2 != nullptr && mat2->GetDebugName() == TerrainMaterial::sTerrainMaterialName) {
        const TerrainMaterial* tMat2 = static_cast<const TerrainMaterial*>(mat2);
        friction2 = tMat2->mFriction;
        restitution2 = tMat2->mRestitution;
        foundCustom = true;
    }

    if (foundCustom) {
        float combinedFriction = std::sqrt(friction1 * friction2);
        float combinedRestitution = std::max(restitution1, restitution2);

        ioSettings.mCombinedFriction = combinedFriction;
        ioSettings.mCombinedRestitution = combinedRestitution;

        // Cache the result so OnContactPersisted can skip the BVH lookup
        uint64_t key = MakeContactKey(
            inBody1.GetID().GetIndexAndSequenceNumber(),
            inBody2.GetID().GetIndexAndSequenceNumber(),
            inManifold.mSubShapeID1.GetValue(),
            inManifold.mSubShapeID2.GetValue());
        std::lock_guard<std::mutex> lock(m_CacheMutex);
        m_ContactCache[key] = { combinedFriction, combinedRestitution };
    }

    // Terrain Interaction Logic
    bool isBody1Terrain = (inBody1.GetObjectLayer() == static_cast<JPH::ObjectLayer>(2));
    const JPH::Body& terrainBody = isBody1Terrain ? inBody1 : inBody2;
    const JPH::Body& otherBody = isBody1Terrain ? inBody2 : inBody1;
    JPH::SubShapeID terrainSubShapeId = isBody1Terrain ? inManifold.mSubShapeID1 : inManifold.mSubShapeID2;
    
    TerrainInteraction::ProcessInteraction(m_WorldRef, m_PhysicsSystem, terrainBody, otherBody, terrainSubShapeId, inManifold, ioSettings, false);
}

/**
 * @brief Called every tick for existing contacts.
 * 
 * Reads from the cache instead of re-traversing the BVH. This is the hot path
 * and must be as fast as possible. Cost: one hash lookup + two float writes.
 */
void ContactListener::OnContactPersisted(const JPH::Body &inBody1, const JPH::Body &inBody2, const JPH::ContactManifold &inManifold, JPH::ContactSettings &ioSettings) {
    // Early-out: skip entirely if neither body is terrain
    if (inBody1.GetObjectLayer() != static_cast<JPH::ObjectLayer>(2) && inBody2.GetObjectLayer() != static_cast<JPH::ObjectLayer>(2)) {
        return;
    }

    uint64_t key = MakeContactKey(
        inBody1.GetID().GetIndexAndSequenceNumber(),
        inBody2.GetID().GetIndexAndSequenceNumber(),
        inManifold.mSubShapeID1.GetValue(),
        inManifold.mSubShapeID2.GetValue());
    std::lock_guard<std::mutex> lock(m_CacheMutex);
    auto it = m_ContactCache.find(key);
    if (it != m_ContactCache.end()) {
        ioSettings.mCombinedFriction = it->second.friction;
        ioSettings.mCombinedRestitution = it->second.restitution;
    }

    // Terrain Interaction Logic
    bool isBody1Terrain = (inBody1.GetObjectLayer() == static_cast<JPH::ObjectLayer>(2));
    const JPH::Body& terrainBody = isBody1Terrain ? inBody1 : inBody2;
    const JPH::Body& otherBody = isBody1Terrain ? inBody2 : inBody1;
    JPH::SubShapeID terrainSubShapeId = isBody1Terrain ? inManifold.mSubShapeID1 : inManifold.mSubShapeID2;

    TerrainInteraction::ProcessInteraction(m_WorldRef, m_PhysicsSystem, terrainBody, otherBody, terrainSubShapeId, inManifold, ioSettings, true);
}

/**
 * @brief Called when a contact is destroyed. Evicts the cached entry.
 */
void ContactListener::OnContactRemoved(const JPH::SubShapeIDPair &inSubShapePair) {
    uint64_t key = MakeContactKey(
        inSubShapePair.GetBody1ID().GetIndexAndSequenceNumber(),
        inSubShapePair.GetBody2ID().GetIndexAndSequenceNumber(),
        inSubShapePair.GetSubShapeID1().GetValue(),
        inSubShapePair.GetSubShapeID2().GetValue());
    std::lock_guard<std::mutex> lock(m_CacheMutex);
    m_ContactCache.erase(key);
}

} // namespace Velthoric

#include <jni.h>
#include <Jolt/Physics/PhysicsSystem.h>

/**
 * @brief JNI Bridge: Instantiates and attaches the ContactListener to the Jolt PhysicsSystem.
 * 
 * @param env The JNI Environment pointer.
 * @param clazz The calling Java class.
 * @param physicsSystemPtr The native virtual address of the Jolt PhysicsSystem.
 * @return The virtual address of the newly allocated ContactListener.
 */
extern "C" JNIEXPORT jlong JNICALL
Java_net_xmx_velthoric_jni_TerrainContactListener_nAttachContactListener(JNIEnv *env, jclass clazz, jlong physicsSystemPtr, jobject world) {
    JPH::PhysicsSystem* ps = reinterpret_cast<JPH::PhysicsSystem*>(physicsSystemPtr);
    if (!ps) return 0;

    auto* listener = new Velthoric::ContactListener(ps, world);

    // Initialize interaction system with current JVM env
    Velthoric::TerrainInteraction::InitJNI(env);

    ps->SetContactListener(listener);
    return reinterpret_cast<jlong>(listener);
}

/**
 * @brief JNI Bridge: Detaches and safely deletes the ContactListener.
 * 
 * @param env The JNI Environment pointer.
 * @param clazz The calling Java class.
 * @param physicsSystemPtr The native virtual address of the Jolt PhysicsSystem.
 * @param listenerPtr The native virtual address of the ContactListener to destroy.
 */
extern "C" JNIEXPORT void JNICALL
Java_net_xmx_velthoric_jni_TerrainContactListener_nDetachContactListener(JNIEnv *env, jclass clazz, jlong physicsSystemPtr, jlong listenerPtr) {
    JPH::PhysicsSystem* ps = reinterpret_cast<JPH::PhysicsSystem*>(physicsSystemPtr);
    if (ps) {
        ps->SetContactListener(nullptr); 
    }
    Velthoric::ContactListener* listener = reinterpret_cast<Velthoric::ContactListener*>(listenerPtr);
    delete listener;
}