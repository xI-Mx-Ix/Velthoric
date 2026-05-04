/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 *
 * Author: xI-Mx-Ix
 */
#include "Velthoric/Terrain/Contact/TerrainContactHandler.h"
#include "Velthoric/Terrain/TerrainGenerator.h"
#include "Velthoric/Terrain/Interaction/TerrainInteraction.h"
#include <Jolt/Physics/Collision/PhysicsMaterial.h>
#include <cmath>
#include <algorithm>

namespace Velthoric {

TerrainContactHandler::TerrainContactHandler(JPH::PhysicsSystem* inPhysicsSystem, jobject inWorldRef)
    : m_PhysicsSystem(inPhysicsSystem), m_WorldRef(inWorldRef) {
    // Initialize all cache shards to empty state
    for (int i = 0; i < NUM_SHARDS; ++i) {
        m_CacheShards[i].nextInsertIdx = 0;
        for (int j = 0; j < ENTRIES_PER_SHARD; ++j) {
            m_CacheShards[i].entries[j].key = 0;
        }
    }
}

/**
 * @brief Handles initial contact for terrain.
 * Extracts material data from Jolt sub-shapes and calculates combined physical properties.
 */
void TerrainContactHandler::OnContactAdded(const JPH::Body &inBody1, const JPH::Body &inBody2, const JPH::ContactManifold &inManifold, JPH::ContactSettings &ioSettings) {
    // Early out if no terrain (Layer 2) is involved
    if (inBody1.GetObjectLayer() != 2 && inBody2.GetObjectLayer() != 2) return;

    uint64_t key = MakeContactKey(inBody1.GetID().GetIndexAndSequenceNumber(), inBody2.GetID().GetIndexAndSequenceNumber(), inManifold.mSubShapeID1.GetValue(), inManifold.mSubShapeID2.GetValue());
    auto& shard = m_CacheShards[key % NUM_SHARDS];
    
    // Attempt cache hit (in case of sub-shape re-entry)
    shard.Lock();
    for (int i = 0; i < ENTRIES_PER_SHARD; ++i) {
        if (shard.entries[i].key == key) {
            ioSettings.mCombinedFriction = shard.entries[i].friction;
            ioSettings.mCombinedRestitution = shard.entries[i].restitution;
            shard.Unlock();
            goto interaction;
        }
    }
    shard.Unlock();

    {
        // Cache Miss: Traverse sub-shapes for materials
        const JPH::PhysicsMaterial* mat1 = inBody1.GetShape()->GetMaterial(inManifold.mSubShapeID1);
        const JPH::PhysicsMaterial* mat2 = inBody2.GetShape()->GetMaterial(inManifold.mSubShapeID2);

        float f1 = inBody1.GetFriction(), r1 = inBody1.GetRestitution();
        float f2 = inBody2.GetFriction(), r2 = inBody2.GetRestitution();
        bool isTerrain = false;

        // Custom material logic for Minecraft-like blocks
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
            float cr = (std::max)(r1, r2);
            ioSettings.mCombinedFriction = cf;
            ioSettings.mCombinedRestitution = cr;

            // Store result in the sharded flat cache (FIFO replacement)
            shard.Lock();
            uint32_t idx = shard.nextInsertIdx;
            shard.entries[idx] = { key, cf, cr };
            shard.nextInsertIdx = (idx + 1) % ENTRIES_PER_SHARD;
            shard.Unlock();
        }
    }

interaction:
    // Process destruction/particle logic in the interaction handler
    bool isBody1Terrain = (inBody1.GetObjectLayer() == 2);
    TerrainInteraction::ProcessInteraction(m_WorldRef, m_PhysicsSystem, 
        isBody1Terrain ? inBody1 : inBody2, 
        isBody1Terrain ? inBody2 : inBody1, 
        isBody1Terrain ? inManifold.mSubShapeID1 : inManifold.mSubShapeID2, 
        inManifold, ioSettings, isBody1Terrain, false);
}

/**
 * @brief Optimized fast-path for persisting contacts.
 * Performs a linear search in the aligned flat cache shard.
 */
void TerrainContactHandler::OnContactPersisted(const JPH::Body &inBody1, const JPH::Body &inBody2, const JPH::ContactManifold &inManifold, JPH::ContactSettings &ioSettings) {
    if (inBody1.GetObjectLayer() != 2 && inBody2.GetObjectLayer() != 2) return;

    uint64_t key = MakeContactKey(inBody1.GetID().GetIndexAndSequenceNumber(), inBody2.GetID().GetIndexAndSequenceNumber(), inManifold.mSubShapeID1.GetValue(), inManifold.mSubShapeID2.GetValue());
    auto& shard = m_CacheShards[key % NUM_SHARDS];
    
    // Fast Lookup: Linear scan is faster than hashing for small entry counts (SIMD-friendly)
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
 * @brief Removes entries from the cache when collision stops.
 */
void TerrainContactHandler::OnContactRemoved(const JPH::SubShapeIDPair &inSubShapePair) {
    uint64_t key = MakeContactKey(inSubShapePair.GetBody1ID().GetIndexAndSequenceNumber(), inSubShapePair.GetBody2ID().GetIndexAndSequenceNumber(), inSubShapePair.GetSubShapeID1().GetValue(), inSubShapePair.GetSubShapeID2().GetValue());
    auto& shard = m_CacheShards[key % NUM_SHARDS];
    
    shard.Lock();
    for (int i = 0; i < ENTRIES_PER_SHARD; ++i) {
        if (shard.entries[i].key == key) {
            shard.entries[i].key = 0; // Invalidate entry
            break;
        }
    }
    shard.Unlock();
}

} // namespace Velthoric

#include <jni.h>
#include <Jolt/Physics/PhysicsSystem.h>

extern "C" {

/**
 * @brief JNI Bridge: Instantiates a new native TerrainContactHandler.
 */
JNIEXPORT jlong JNICALL
Java_net_xmx_velthoric_jni_TerrainContactHandler_nCreateHandler(JNIEnv *env, jclass clazz, jlong physicsSystemPtr, jobject world) {
    (void)clazz;
    JPH::PhysicsSystem* ps = reinterpret_cast<JPH::PhysicsSystem*>(physicsSystemPtr);
    if (!ps) return 0;

    auto* handler = new Velthoric::TerrainContactHandler(ps, world);
    return reinterpret_cast<jlong>(handler);
}

/**
 * @brief JNI Bridge: Safely deletes the native TerrainContactHandler.
 */
JNIEXPORT void JNICALL
Java_net_xmx_velthoric_jni_TerrainContactHandler_nDestroyHandler(JNIEnv *env, jclass clazz, jlong address) {
    (void)env; (void)clazz;
    Velthoric::TerrainContactHandler* handler = reinterpret_cast<Velthoric::TerrainContactHandler*>(address);
    delete handler;
}

}