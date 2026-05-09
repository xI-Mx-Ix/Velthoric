/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 *
 * Author: xI-Mx-Ix
 */
#include "Velthoric/Terrain/Interaction/TerrainInteraction.h"
#include "Velthoric/Terrain/Generation/TerrainGenerator.h"
#include "Velthoric/Terrain/Shape/TerrainVoxelShape.h"
#include <Jolt/Physics/Collision/PhysicsMaterial.h>
#include <Jolt/Physics/PhysicsSystem.h>
#include <Jolt/Physics/Collision/Shape/StaticCompoundShape.h>
#include <algorithm>
#include <cmath>
#include <cstring>
#include <random>
#include <jni.h>

namespace Velthoric {

/** Static storage and atomic initializations. */
TerrainInteraction::Config TerrainInteraction::s_Config;
std::array<TerrainInteraction::InternalMaterialProps, 65536> TerrainInteraction::s_MaterialProps;
jclass TerrainInteraction::s_HandlerClass = nullptr;
std::atomic<int> TerrainInteraction::s_EventCount{0};
std::atomic<int> TerrainInteraction::s_TickBreaks{0};
std::atomic<int> TerrainInteraction::s_TickTransforms{0};
std::atomic<int> TerrainInteraction::s_TickParticles{0};
std::atomic<int> TerrainInteraction::s_TickImpacts{0};
std::array<std::atomic<int>, 65536> TerrainInteraction::s_BodyContactCounts;
TerrainInteraction::Shard TerrainInteraction::s_Shards[TerrainInteraction::NUM_SHARDS];

/**
 * @brief Bootstraps the JNI environment.
 * 
 * Locates the Java interaction handler class for future event processing.
 * 
 * @param env JNI Environment pointer.
 */
void TerrainInteraction::InitJNI(JNIEnv* env) {
    if (s_HandlerClass || !env) return;
    jclass localClass = env->FindClass("net/xmx/velthoric/core/terrain/interaction/VxTerrainInteractionHandler");
    if (localClass) {
        s_HandlerClass = (jclass)env->NewGlobalRef(localClass);
    }
}

/**
 * @brief Queues an interaction event for later flushing to Java.
 * 
 * Uses a sharded ringbuffer to minimize lock contention between physics worker threads.
 * 
 * @param event The event data to queue.
 */
void TerrainInteraction::QueueEvent(const InteractionEvent& event) {
    // Distribute by body and subshape to balance shard load
    uint32_t shardIdx = (event.terrainBodyId ^ event.subShapeId) % NUM_SHARDS;
    auto& shard = s_Shards[shardIdx];

    shard.Lock();
    if (shard.Enqueue(event)) {
        s_EventCount.fetch_add(1, std::memory_order_relaxed);
    }
    shard.Unlock();
}

/**
 * @brief Populates the material interaction lookup table.
 * 
 * @param configs Pointer to material data from Java.
 * @param count Number of materials.
 */
void TerrainInteraction::RegisterMaterials(const MaterialConfig* configs, int count) {
    for (int i = 0; i < count; ++i) {
        uint32_t id = configs[i].materialId;
        if (id < 65536) {
            s_MaterialProps[id].isFragile = configs[i].isFragile;
            s_MaterialProps[id].isTransformable = configs[i].isTransformable;
            s_MaterialProps[id].spawnsParticles = configs[i].spawnsParticles;
            s_MaterialProps[id].breakThreshold = configs[i].breakThreshold;
            s_MaterialProps[id].isInteractable = configs[i].isInteractable;
            s_MaterialProps[id].interactThreshold = configs[i].interactThreshold;
            s_MaterialProps[id].transformThreshold = configs[i].transformThreshold;
        }
    }
}

/**
 * @brief High-performance interaction logic executed during physics ticks.
 * 
 * This method calculates impact energy, sliding forces, and triggers events.
 * It is highly optimized:
 * - Uses Lazy evaluation for block positions to avoid expensive rotation math.
 * - Employs sharded spinlocks for event queuing.
 * - Uses lossy deduplication to filter redundant impacts in O(1).
 */
void TerrainInteraction::ProcessInteraction(jobject world, const JPH::PhysicsSystem* ps,
                                           const JPH::Body& terrainBody, const JPH::Body& otherBody, 
                                           JPH::SubShapeID subShapeId,
                                           const JPH::ContactManifold& manifold, 
                                           JPH::ContactSettings& settings, 
                                           bool terrainIsBody1,
                                           bool isPersisted,
                                           uint32_t materialId) {
    (void)world;
    const JPH::Shape* shape = terrainBody.GetShape();
    if (!shape) return;

    uint32_t matId = materialId;
    if (matId == 0) {
        // Fast material lookup path
        const JPH::PhysicsMaterial* mat = shape->GetMaterial(subShapeId);
        matId = 1;
        if (mat) {
            const char* name = mat->GetDebugName();
            // Compare pointers first (for speed), then string content
            if (name == TerrainMaterial::sTerrainMaterialName || (name && strcmp(name, TerrainMaterial::sTerrainMaterialName) == 0)) {
                matId = static_cast<const TerrainMaterial*>(mat)->mMaterialId;
            }
        }
    }

    const auto& props = s_MaterialProps[matId < 65536 ? matId : 1];
    // Early exit: Material doesn't support interaction
    if (!props.isFragile && !props.isTransformable && !props.spawnsParticles && !props.isInteractable && matId != 1) return;

    uint32_t terrainId = terrainBody.GetID().GetIndex();
    uint32_t subIdVal = subShapeId.GetValue();
    uint64_t blockKey = (static_cast<uint64_t>(terrainId) << 32) | subIdVal;
    auto& shard = s_Shards[blockKey % NUM_SHARDS];

    // Lazy position calculation: Only computed if an event is actually triggered.
    bool posCalculated = false;
    JPH::RVec3 blockWorldPos;
    auto getBlockPos = [&]() -> JPH::RVec3 {
        if (posCalculated) return blockWorldPos;
        const JPH::StaticCompoundShape* compound = nullptr;
        if (shape->GetSubType() == JPH::EShapeSubType::StaticCompound) {
            compound = static_cast<const JPH::StaticCompoundShape*>(shape);
        } else if (shape->GetSubType() == JPH::EShapeSubType::User1) {
            compound = static_cast<const Velthoric::TerrainVoxelShape*>(shape)->mCompoundShape.GetPtr();
        }
        
        if (compound) {
            JPH::SubShapeID remainder;
            uint32_t idx = compound->GetSubShapeIndexFromID(subShapeId, remainder);
            if (idx < compound->GetNumSubShapes()) {
                blockWorldPos = terrainBody.GetCenterOfMassPosition() + terrainBody.GetRotation() * compound->GetSubShape(idx).GetPositionCOM();
                posCalculated = true;
                return blockWorldPos;
            }
        }
        blockWorldPos = manifold.GetWorldSpaceContactPointOn1(0);
        posCalculated = true;
        return blockWorldPos;
    };

    // Baseline physics properties
    float invMass = otherBody.GetMotionProperties() ? otherBody.GetMotionProperties()->GetInverseMass() : 0.0f;
    float otherMass = invMass > 0.0f ? 1.0f / invMass : s_Config.massBaseline;
    float massScale = (std::min)(s_Config.massMaxScale, (std::max)(s_Config.massMinScale, otherMass / s_Config.massBaseline));
    float gravity = ps ? std::abs(ps->GetGravity().GetY()) : 9.81f;
    uint32_t otherBodyIdx = otherBody.GetID().GetIndex();

    // Iterate over contact points
    int numPoints = (int)manifold.mRelativeContactPointsOn1.size();
    for (int i = 0; i < numPoints; ++i) {
        JPH::RVec3 p = manifold.GetWorldSpaceContactPointOn1(i);
        JPH::Vec3 relVel = otherBody.GetPointVelocity(p) - terrainBody.GetPointVelocity(p);
        float normalSpeed = std::abs(relVel.Dot(manifold.mWorldSpaceNormal));
        
        // Estimated normal force based on impact speed and penetration
        float kineticForce = normalSpeed * otherMass;
        float staticForce = manifold.mPenetrationDepth * otherMass * gravity;
        
        // Weight distribution: Divide force by the total number of terrain contacts the body has.
        int contactCount = GetContactCount(otherBodyIdx);
        float distributedMassScale = 1.0f / (float)(std::max)(1, contactCount);
        
        float totalForce = (kineticForce + staticForce) * distributedMassScale;

        /** Lambda to fill common event fields. */
        auto populateEvent = [&](InteractionEvent& ev, InteractionType type, float strength) {
            ev.type = type; ev.materialId = matId; ev.subShapeId = subIdVal; ev.terrainBodyId = terrainId;
            JPH::RVec3 bPos = getBlockPos();
            ev.x1 = (float)bPos.GetX(); ev.y1 = (float)bPos.GetY(); ev.z1 = (float)bPos.GetZ();
            ev.strength = strength;
        };

        // 1. Block destruction (fragile)
        // Uses totalForce (Kinetic + Static) so objects can crush glass just by resting on it
        if (props.isFragile && totalForce > props.breakThreshold) {
            shard.Lock();
            if (shard.TryDeduplicate(blockKey)) {
                if (s_TickBreaks.fetch_add(1, std::memory_order_relaxed) < s_Config.maxBreaksPerTick) {
                    InteractionEvent ev; populateEvent(ev, InteractionType::BLOCK_BREAK, totalForce);
                    shard.Enqueue(ev); s_EventCount.fetch_add(1, std::memory_order_relaxed);
                }
            }
            shard.Unlock();
        } 
        // 2. Block transformation (wear)
        else if (props.isTransformable && totalForce > props.transformThreshold) {
            shard.Lock();
            if (shard.TryDeduplicate(blockKey ^ 0x12345678)) {
                if (s_TickTransforms.fetch_add(1, std::memory_order_relaxed) < s_Config.maxTransformsPerTick) {
                    InteractionEvent ev; populateEvent(ev, InteractionType::BLOCK_TRANSFORM, totalForce);
                    shard.Enqueue(ev); s_EventCount.fetch_add(1, std::memory_order_relaxed);
                }
            }
            shard.Unlock();
        }

        // 3. Generic interaction (doors/gates)
        // Uses strictly kineticForce! Doors should open from impacts (hits/bumps), not from slow pressure/penetration
        if (props.isInteractable && kineticForce > props.interactThreshold) {
            shard.Lock();
            if (shard.TryDeduplicate(blockKey ^ 0x9ABCDEF0)) {
                InteractionEvent ev; populateEvent(ev, InteractionType::BLOCK_INTERACT, kineticForce);
                JPH::Vec3 normal = terrainIsBody1 ? -manifold.mWorldSpaceNormal : manifold.mWorldSpaceNormal;
                ev.x2 = (float)normal.GetX(); ev.y2 = (float)normal.GetY(); ev.z2 = (float)normal.GetZ();
                shard.Enqueue(ev); s_EventCount.fetch_add(1, std::memory_order_relaxed);
            }
            shard.Unlock();
        }

        // 4. Impact particles
        // Fires once per unique contact-point when a body initially hits terrain.
        if (props.spawnsParticles && !isPersisted && normalSpeed >= s_Config.impactMinNormalSpeed) {
            float energy = (normalSpeed + manifold.mPenetrationDepth * 10.0f) * massScale;
            if (energy > s_Config.particleImpactEnergyThreshold) {
                // Per-contact-point dedup: body + subshape + point index
                uint64_t impactKey = (static_cast<uint64_t>(otherBodyIdx) << 32) | (subIdVal ^ (static_cast<uint32_t>(i) * 0x9E3779B9));
                auto& impShard = s_Shards[impactKey % NUM_SHARDS];
                impShard.Lock();
                if (impShard.TryDeduplicate(impactKey)) {
                    if (s_TickImpacts.fetch_add(1, std::memory_order_relaxed) < s_Config.maxImpactsPerTick) {
                        InteractionEvent ev; populateEvent(ev, InteractionType::TERRAIN_IMPACT, energy);
                        ev.x2 = (float)p.GetX(); ev.y2 = (float)p.GetY(); ev.z2 = (float)p.GetZ();
                        impShard.Enqueue(ev); s_EventCount.fetch_add(1, std::memory_order_relaxed);
                    }
                }
                impShard.Unlock();
            }
        }

        // 5. Sliding particles
        if (props.spawnsParticles && isPersisted) {
            float slidingSpeed = (relVel - relVel.Dot(manifold.mWorldSpaceNormal) * manifold.mWorldSpaceNormal).Length();
            if (slidingSpeed > s_Config.particleSlidingVelocityThreshold) {
                float energy = (slidingSpeed * settings.mCombinedFriction + manifold.mPenetrationDepth * 5.0f) * massScale;
                if (energy > s_Config.particleSlidingEnergyThreshold) {
                    // High-performance RNG without locking
                    thread_local std::mt19937 rng(std::random_device{}());
                    thread_local std::uniform_real_distribution<float> dist(0.0f, 1.0f);
                    if (dist(rng) < (std::min)(s_Config.particleSlidingChanceMax, energy * s_Config.particleSlidingChanceMult)) {
                        shard.Lock();
                        if (shard.TryDeduplicate(blockKey ^ 0xFAFBF000)) {
                            if (s_TickParticles.fetch_add(1, std::memory_order_relaxed) < s_Config.maxParticlesPerTick) {
                                InteractionEvent ev; populateEvent(ev, InteractionType::TERRAIN_SLIDE, energy);
                                ev.x2 = (float)p.GetX(); ev.y2 = (float)p.GetY(); ev.z2 = (float)p.GetZ();
                                shard.Enqueue(ev); s_EventCount.fetch_add(1, std::memory_order_relaxed);
                            }
                        }
                        shard.Unlock();
                    }
                }
            }
        }
    }
}

/**
 * @brief Transfers native interaction events to the Java-side buffer.
 * 
 * Clears deduplication tables and resets rate-limiting budgets for the next tick.
 */
int TerrainInteraction::FlushEvents(InteractionEvent* outBuffer, int maxCount) {
    s_TickBreaks.store(0, std::memory_order_relaxed);
    s_TickTransforms.store(0, std::memory_order_relaxed);
    s_TickParticles.store(0, std::memory_order_relaxed);
    s_TickImpacts.store(0, std::memory_order_relaxed);

    int total = 0;
    for (int i = 0; i < NUM_SHARDS; ++i) {
        auto& shard = s_Shards[i];
        shard.Lock();
        // Full clear of lossy deduplication table at tick start
        std::memset(shard.dedupTable.data(), 0, shard.dedupTable.size() * sizeof(uint64_t));
        
        // Drain ringbuffer
        while (shard.head != shard.tail && total < maxCount) {
            outBuffer[total++] = shard.queue[shard.head];
            shard.head = (shard.head + 1) % QUEUE_SIZE_PER_SHARD;
        }
        shard.Unlock();
        if (total >= maxCount) break;
    }
    s_EventCount.store(0, std::memory_order_relaxed);
    return total;
}

} // namespace Velthoric

extern "C" {
/**
 * @brief JNI Endpoint: Bulk register material configs.
 */
JNIEXPORT void JNICALL
Java_net_xmx_velthoric_jni_TerrainInteraction_nRegisterInteractionMaterials(JNIEnv *env, jclass clazz, jobject buffer, jint count) {
    (void)clazz;
    if (!buffer || count <= 0) return;
    const Velthoric::TerrainInteraction::MaterialConfig* configs = static_cast<const Velthoric::TerrainInteraction::MaterialConfig*>(env->GetDirectBufferAddress(buffer));
    if (configs) Velthoric::TerrainInteraction::RegisterMaterials(configs, count);
}

/**
 * @brief JNI Endpoint: Flush interaction events to Java memory.
 */
JNIEXPORT jint JNICALL
Java_net_xmx_velthoric_jni_TerrainInteraction_nFlushEvents(JNIEnv *env, jclass clazz, jobject buffer, jint maxCount) {
    (void)env; (void)clazz;
    if (!buffer || maxCount <= 0) return 0;
    Velthoric::TerrainInteraction::InteractionEvent* dest = static_cast<Velthoric::TerrainInteraction::InteractionEvent*>(env->GetDirectBufferAddress(buffer));
    if (dest) return Velthoric::TerrainInteraction::FlushEvents(dest, maxCount);
    return 0;
}

/**
 * @brief JNI Endpoint: Update interaction configuration.
 */
JNIEXPORT void JNICALL
Java_net_xmx_velthoric_jni_TerrainInteraction_nSetInteractionConfig(JNIEnv *env, jclass clazz, jobject buffer) {
    (void)clazz;
    if (!buffer) return;
    const Velthoric::TerrainInteraction::Config* config = static_cast<const Velthoric::TerrainInteraction::Config*>(env->GetDirectBufferAddress(buffer));
    if (config) Velthoric::TerrainInteraction::SetConfig(*config);
}

}