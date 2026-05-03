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
JPH::ValidateResult ContactListener::OnContactValidate(const JPH::Body&, const JPH::Body&, JPH::RVec3Arg, const JPH::CollideShapeResult&) {
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
    if (inBody1.GetObjectLayer() != static_cast<JPH::ObjectLayer>(2) && inBody2.GetObjectLayer() != static_cast<JPH::ObjectLayer>(2)) return;

    uint64_t key = MakeContactKey(inBody1.GetID().GetIndexAndSequenceNumber(), inBody2.GetID().GetIndexAndSequenceNumber(), inManifold.mSubShapeID1.GetValue(), inManifold.mSubShapeID2.GetValue());
    auto& shard = m_CacheShards[key % NUM_SHARDS];
    
    // Check if we already have combined settings in the cache for this specific contact
    {
        std::lock_guard<std::mutex> lock(shard.mutex);
        auto it = shard.entries.find(key);
        if (it != shard.entries.end()) {
            ioSettings.mCombinedFriction = it->second.friction;
            ioSettings.mCombinedRestitution = it->second.restitution;
        }
    }

    // Full material extraction (expensive BVH traversal)
    const JPH::PhysicsMaterial* mat1 = inBody1.GetShape()->GetMaterial(inManifold.mSubShapeID1);
    const JPH::PhysicsMaterial* mat2 = inBody2.GetShape()->GetMaterial(inManifold.mSubShapeID2);

    float f1 = inBody1.GetFriction(), r1 = inBody1.GetRestitution();
    float f2 = inBody2.GetFriction(), r2 = inBody2.GetRestitution();
    bool found = false;

    if (mat1 && mat1->GetDebugName() == TerrainMaterial::sTerrainMaterialName) {
        auto* tm = static_cast<const TerrainMaterial*>(mat1);
        f1 = tm->mFriction; r1 = tm->mRestitution; found = true;
    }
    if (mat2 && mat2->GetDebugName() == TerrainMaterial::sTerrainMaterialName) {
        auto* tm = static_cast<const TerrainMaterial*>(mat2);
        f2 = tm->mFriction; r2 = tm->mRestitution; found = true;
    }

    if (found) {
        float cf = std::sqrt(f1 * f2);
        float cr = std::max(r1, r2);
        ioSettings.mCombinedFriction = cf;
        ioSettings.mCombinedRestitution = cr;

        std::lock_guard<std::mutex> lock(shard.mutex);
        shard.entries[key] = { cf, cr };
    }

    // Terrain Interaction Logic
    bool isBody1Terrain = (inBody1.GetObjectLayer() == static_cast<JPH::ObjectLayer>(2));
    const JPH::Body& terrainBody = isBody1Terrain ? inBody1 : inBody2;
    const JPH::Body& otherBody = isBody1Terrain ? inBody2 : inBody1;
    JPH::SubShapeID terrainSubShapeId = isBody1Terrain ? inManifold.mSubShapeID1 : inManifold.mSubShapeID2;
    
    TerrainInteraction::ProcessInteraction(m_WorldRef, m_PhysicsSystem, terrainBody, otherBody, terrainSubShapeId, inManifold, ioSettings, isBody1Terrain, false);
}

/**
 * @brief Called every tick for existing contacts.
 * 
 * Reads from the sharded cache instead of re-traversing the BVH. This is the hot path
 * and must be as fast as possible.
 */
void ContactListener::OnContactPersisted(const JPH::Body &inBody1, const JPH::Body &inBody2, const JPH::ContactManifold &inManifold, JPH::ContactSettings &ioSettings) {
    if (inBody1.GetObjectLayer() != static_cast<JPH::ObjectLayer>(2) && inBody2.GetObjectLayer() != static_cast<JPH::ObjectLayer>(2)) return;

    uint64_t key = MakeContactKey(inBody1.GetID().GetIndexAndSequenceNumber(), inBody2.GetID().GetIndexAndSequenceNumber(), inManifold.mSubShapeID1.GetValue(), inManifold.mSubShapeID2.GetValue());
    auto& shard = m_CacheShards[key % NUM_SHARDS];
    {
        std::lock_guard<std::mutex> lock(shard.mutex);
        auto it = shard.entries.find(key);
        if (it != shard.entries.end()) {
            ioSettings.mCombinedFriction = it->second.friction;
            ioSettings.mCombinedRestitution = it->second.restitution;
        }
    }

    // Terrain Interaction Logic
    bool isBody1Terrain = (inBody1.GetObjectLayer() == static_cast<JPH::ObjectLayer>(2));
    const JPH::Body& terrainBody = isBody1Terrain ? inBody1 : inBody2;
    const JPH::Body& otherBody = isBody1Terrain ? inBody2 : inBody1;
    JPH::SubShapeID terrainSubShapeId = isBody1Terrain ? inManifold.mSubShapeID1 : inManifold.mSubShapeID2;

    TerrainInteraction::ProcessInteraction(m_WorldRef, m_PhysicsSystem, terrainBody, otherBody, terrainSubShapeId, inManifold, ioSettings, isBody1Terrain, true);
}

/**
 * @brief Called when a contact is destroyed. Evicts the cached entry from its shard.
 */
void ContactListener::OnContactRemoved(const JPH::SubShapeIDPair &inSubShapePair) {
    uint64_t key = MakeContactKey(inSubShapePair.GetBody1ID().GetIndexAndSequenceNumber(), inSubShapePair.GetBody2ID().GetIndexAndSequenceNumber(), inSubShapePair.GetSubShapeID1().GetValue(), inSubShapePair.GetSubShapeID2().GetValue());
    auto& shard = m_CacheShards[key % NUM_SHARDS];
    std::lock_guard<std::mutex> lock(shard.mutex);
    shard.entries.erase(key);
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
 * @param world The Velthoric world object reference.
 * @return The virtual address of the newly allocated ContactListener.
 */
extern "C" JNIEXPORT jlong JNICALL
Java_net_xmx_velthoric_jni_TerrainContactListener_nAttachContactListener(JNIEnv *env, jclass clazz, jlong physicsSystemPtr, jobject world) {
    (void)clazz;
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
    (void)env; (void)clazz;
    JPH::PhysicsSystem* ps = reinterpret_cast<JPH::PhysicsSystem*>(physicsSystemPtr);
    if (ps) {
        ps->SetContactListener(nullptr); 
    }
    Velthoric::ContactListener* listener = reinterpret_cast<Velthoric::ContactListener*>(listenerPtr);
    delete listener;
}