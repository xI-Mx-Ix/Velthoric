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

ContactListener::ContactListener(JPH::PhysicsSystem* inPhysicsSystem, jobject inWorldRef) 
    : m_PhysicsSystem(inPhysicsSystem)
    , m_BodyPairIgnoreManager(nullptr) {
    JNIEnv* env = nullptr;
    JavaVM* vm = nullptr;
    jint res = JNI_GetCreatedJavaVMs(&vm, 1, nullptr);
    if (res == JNI_OK && vm->GetEnv((void**)&env, JNI_VERSION_1_8) == JNI_OK) {
        m_WorldRef = env->NewGlobalRef(inWorldRef);
    } else {
        m_WorldRef = nullptr;
    }

    // Initialize all shards to empty status (key 0)
    for (int i = 0; i < NUM_SHARDS; ++i) {
        m_CacheShards[i].nextInsertIdx = 0;
        for (int j = 0; j < ENTRIES_PER_SHARD; ++j) {
            m_CacheShards[i].entries[j].key = 0;
        }
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
 * @brief Dependency Injection: Set the BodyPairIgnoreManager.
 * 
 * @param inManager Pointer to the manager instance managed by VxPhysicsWorld.
 */
void ContactListener::SetBodyPairIgnoreManager(BodyPairIgnoreManager* inManager) {
    m_BodyPairIgnoreManager = inManager;
}

/**
 * @brief Returns the current ignore manager.
 * 
 * @return BodyPairIgnoreManager* Current pointer or nullptr.
 */
BodyPairIgnoreManager* ContactListener::GetBodyPairIgnoreManager() const {
    return m_BodyPairIgnoreManager;
}

/**
 * @brief Pre-collision filter.
 * 
 * Uses an ultra-fast early-out if the ignore manager reports no active filters.
 * Otherwise, checks if the specific body pair should be rejected (e.g., spawn protection).
 */
JPH::ValidateResult ContactListener::OnContactValidate(const JPH::Body& inBody1, const JPH::Body& inBody2, JPH::RVec3Arg, const JPH::CollideShapeResult&) {
    // Ultra-fast path: If no pairs are ignored at all globally, skip the logic
    if (m_BodyPairIgnoreManager && m_BodyPairIgnoreManager->HasIgnoredPairs()) {
        uint32_t bodyId1 = inBody1.GetID().GetIndexAndSequenceNumber();
        uint32_t bodyId2 = inBody2.GetID().GetIndexAndSequenceNumber();
        if (m_BodyPairIgnoreManager->ShouldIgnorePair(bodyId1, bodyId2)) {
            return JPH::ValidateResult::RejectContact;
        }
    }
    return JPH::ValidateResult::AcceptAllContactsForThisBodyPair;
}

/**
 * @brief Entry point for new contacts.
 * 
 * Performs expensive material data extraction only on cache miss.
 * Combines friction and restitution and stores the result in the sharded flat cache.
 */
void ContactListener::OnContactAdded(const JPH::Body &inBody1, const JPH::Body &inBody2, const JPH::ContactManifold &inManifold, JPH::ContactSettings &ioSettings) {
    // Optimization: Physical interactions and material overrides only apply if terrain is involved (Layer 2)
    if (inBody1.GetObjectLayer() != 2 && inBody2.GetObjectLayer() != 2) return;

    uint64_t key = MakeContactKey(inBody1.GetID().GetIndexAndSequenceNumber(), inBody2.GetID().GetIndexAndSequenceNumber(), inManifold.mSubShapeID1.GetValue(), inManifold.mSubShapeID2.GetValue());
    auto& shard = m_CacheShards[key % NUM_SHARDS];
    
    // Quick search in shard before doing expensive material extraction
    shard.Lock();
    for (int i = 0; i < ENTRIES_PER_SHARD; ++i) {
        if (shard.entries[i].key == key) {
            ioSettings.mCombinedFriction = shard.entries[i].friction;
            ioSettings.mCombinedRestitution = shard.entries[i].restitution;
            shard.Unlock();
            goto interaction; // Found in cache, bypass material logic
        }
    }
    shard.Unlock();

    {
        // Cache miss: Traverses Jolt BVH to get sub-shape materials
        const JPH::PhysicsMaterial* mat1 = inBody1.GetShape()->GetMaterial(inManifold.mSubShapeID1);
        const JPH::PhysicsMaterial* mat2 = inBody2.GetShape()->GetMaterial(inManifold.mSubShapeID2);

        float f1 = inBody1.GetFriction(), r1 = inBody1.GetRestitution();
        float f2 = inBody2.GetFriction(), r2 = inBody2.GetRestitution();
        bool isTerrain = false;

        // Custom TerrainMaterial handling for friction/restitution overrides
        if (mat1 && mat1->GetDebugName() == TerrainMaterial::sTerrainMaterialName) {
            auto* tm = static_cast<const TerrainMaterial*>(mat1);
            f1 = tm->mFriction; r1 = tm->mRestitution; isTerrain = true;
        }
        if (mat2 && mat2->GetDebugName() == TerrainMaterial::sTerrainMaterialName) {
            auto* tm = static_cast<const TerrainMaterial*>(mat2);
            f2 = tm->mFriction; r2 = tm->mRestitution; isTerrain = true;
        }

        if (isTerrain) {
            float cf = std::sqrt(f1 * f2);
            float cr = std::max(r1, r2);
            ioSettings.mCombinedFriction = cf;
            ioSettings.mCombinedRestitution = cr;

            // Store in cache using FIFO replacement strategy
            shard.Lock();
            uint32_t idx = shard.nextInsertIdx;
            shard.entries[idx] = { key, cf, cr };
            shard.nextInsertIdx = (idx + 1) % ENTRIES_PER_SHARD;
            shard.Unlock();
        }
    }

interaction:
    // Forward to terrain interaction pipeline (destruction, particles, etc.)
    bool isBody1Terrain = (inBody1.GetObjectLayer() == 2);
    TerrainInteraction::ProcessInteraction(m_WorldRef, m_PhysicsSystem, 
        isBody1Terrain ? inBody1 : inBody2, 
        isBody1Terrain ? inBody2 : inBody1, 
        isBody1Terrain ? inManifold.mSubShapeID1 : inManifold.mSubShapeID2, 
        inManifold, ioSettings, isBody1Terrain, false);
}

/**
 * @brief Hot path for existing contacts.
 * 
 * Uses atomic spinlocks to quickly retrieve material properties from the cache.
 * Linear search over 64 entries is used for maximum cache locality.
 */
void ContactListener::OnContactPersisted(const JPH::Body &inBody1, const JPH::Body &inBody2, const JPH::ContactManifold &inManifold, JPH::ContactSettings &ioSettings) {
    if (inBody1.GetObjectLayer() != 2 && inBody2.GetObjectLayer() != 2) return;

    uint64_t key = MakeContactKey(inBody1.GetID().GetIndexAndSequenceNumber(), inBody2.GetID().GetIndexAndSequenceNumber(), inManifold.mSubShapeID1.GetValue(), inManifold.mSubShapeID2.GetValue());
    auto& shard = m_CacheShards[key % NUM_SHARDS];
    
    // FAST PATH: Linear search in aligned memory block
    shard.Lock();
    for (int i = 0; i < ENTRIES_PER_SHARD; ++i) {
        if (shard.entries[i].key == key) {
            ioSettings.mCombinedFriction = shard.entries[i].friction;
            ioSettings.mCombinedRestitution = shard.entries[i].restitution;
            break;
        }
    }
    shard.Unlock();

    bool isBody1Terrain = (inBody1.GetObjectLayer() == 2);
    TerrainInteraction::ProcessInteraction(m_WorldRef, m_PhysicsSystem, 
        isBody1Terrain ? inBody1 : inBody2, 
        isBody1Terrain ? inBody2 : inBody1, 
        isBody1Terrain ? inManifold.mSubShapeID1 : inManifold.mSubShapeID2, 
        inManifold, ioSettings, isBody1Terrain, true);
}

/**
 * @brief Removes contact metadata from the cache.
 */
void ContactListener::OnContactRemoved(const JPH::SubShapeIDPair &inSubShapePair) {
    uint64_t key = MakeContactKey(inSubShapePair.GetBody1ID().GetIndexAndSequenceNumber(), inSubShapePair.GetBody2ID().GetIndexAndSequenceNumber(), inSubShapePair.GetSubShapeID1().GetValue(), inSubShapePair.GetSubShapeID2().GetValue());
    auto& shard = m_CacheShards[key % NUM_SHARDS];
    
    shard.Lock();
    for (int i = 0; i < ENTRIES_PER_SHARD; ++i) {
        if (shard.entries[i].key == key) {
            shard.entries[i].key = 0; // Invalidate cache entry
            break;
        }
    }
    shard.Unlock();
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

    // Initialize interaction system with current JVM env for later callbacks
    Velthoric::TerrainInteraction::InitJNI(env);

    ps->SetContactListener(listener);
    return reinterpret_cast<jlong>(listener);
}

/**
 * @brief JNI Bridge: Sets the body pair ignore manager on the ContactListener.
 * 
 * @param env The JNI Environment pointer.
 * @param clazz The calling Java class.
 * @param listenerPtr The native virtual address of the ContactListener.
 * @param managerPtr The native virtual address of the BodyPairIgnoreManager (0 to clear).
 */
extern "C" JNIEXPORT void JNICALL
Java_net_xmx_velthoric_jni_TerrainContactListener_nSetBodyPairIgnoreManager(JNIEnv *env, jclass clazz, jlong listenerPtr, jlong managerPtr) {
    (void)env;
    (void)clazz;
    Velthoric::ContactListener* listener = reinterpret_cast<Velthoric::ContactListener*>(listenerPtr);
    Velthoric::BodyPairIgnoreManager* manager = reinterpret_cast<Velthoric::BodyPairIgnoreManager*>(managerPtr);
    if (listener) {
        listener->SetBodyPairIgnoreManager(manager);
    }
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