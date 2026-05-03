/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 *
 * Author: xI-Mx-Ix
 */
#include "TerrainInteraction.h"
#include "../TerrainGenerator.h"
#include <Jolt/Physics/Collision/PhysicsMaterial.h>
#include <Jolt/Physics/PhysicsSystem.h>
#include <Jolt/Physics/Collision/Shape/StaticCompoundShape.h>
#include <algorithm>
#include <cmath>
#include <cstring>
#include <random>
#include <jni.h>

namespace Velthoric {

/** Static storage for material interaction properties. */
TerrainInteraction::Config TerrainInteraction::s_Config;
std::vector<TerrainInteraction::InternalMaterialProps> TerrainInteraction::s_MaterialProps(65536);

/** JNI Caching and Event Queuing. */
jclass TerrainInteraction::s_HandlerClass = nullptr;
jmethodID TerrainInteraction::s_BreakMethod = nullptr;
jmethodID TerrainInteraction::s_TransformMethod = nullptr;
jmethodID TerrainInteraction::s_ParticleMethod = nullptr;
jmethodID TerrainInteraction::s_InteractMethod = nullptr;

std::vector<TerrainInteraction::InteractionEvent> TerrainInteraction::s_EventQueue;
std::mutex TerrainInteraction::s_QueueMutex;
std::atomic<int> TerrainInteraction::s_EventCount{0};
std::atomic<int> TerrainInteraction::s_TickBreaks{0};
std::atomic<int> TerrainInteraction::s_TickTransforms{0};
std::atomic<int> TerrainInteraction::s_TickParticles{0};

/** Sharded storage for events and deduplication. */
TerrainInteraction::Shard TerrainInteraction::s_Shards[TerrainInteraction::NUM_SHARDS];

/**
 * @brief Bootstraps the interaction system's JNI bridge.
 * 
 * Locates the Java handler class and initializes global references to allow 
 * asynchronous callbacks from native code.
 * 
 * @param env The JNI Environment pointer.
 */
void TerrainInteraction::InitJNI(JNIEnv* env) {
    if (s_HandlerClass || !env) return;

    jclass localClass = env->FindClass("net/xmx/velthoric/core/terrain/interaction/VxTerrainInteractionHandler");
    if (localClass) {
        s_HandlerClass = (jclass)env->NewGlobalRef(localClass);
        s_MaterialProps[1].spawnsParticles = true;
    }
}

/**
 * @brief Internal helper to push events into the thread-safe sharded queue.
 * 
 * Distributes events across multiple shards based on body and sub-shape IDs 
 * to eliminate lock contention between physics worker threads.
 * 
 * @param event The interaction event to queue.
 */
void TerrainInteraction::QueueEvent(const InteractionEvent& event) {
    uint32_t shardIdx = (event.terrainBodyId ^ event.subShapeId) % NUM_SHARDS;
    auto& shard = s_Shards[shardIdx];

    std::lock_guard<std::mutex> lock(shard.queueMutex);
    if (shard.queue.size() < (32768 / NUM_SHARDS)) {
        shard.queue.push_back(event);
        s_EventCount++;
    }
}

/**
 * @brief Updates the native lookup table for material interaction properties.
 * 
 * @param configs Pointer to the material configuration array passed from Java.
 * @param count   The number of material configurations.
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
        }
    }
}

/**
 * @brief Performs real-time interaction logic during a physics contact event.
 *
 * Evaluates impact energy and sliding friction to trigger block destruction,
 * soil transformation, or visual effects.
 */
void TerrainInteraction::ProcessInteraction(jobject world, const JPH::PhysicsSystem* ps,
                                          const JPH::Body& terrainBody, const JPH::Body& otherBody, 
                                          JPH::SubShapeID subShapeId,
                                          const JPH::ContactManifold& manifold, 
                                          JPH::ContactSettings& settings, 
                                          bool isPersisted) {
    (void)world;
    if (terrainBody.GetShape() == nullptr) return;

    // Fast-path: check material first
    const JPH::PhysicsMaterial* mat = terrainBody.GetShape()->GetMaterial(subShapeId);
    uint32_t matId = 1;
    if (mat && mat->GetDebugName() && strcmp(mat->GetDebugName(), TerrainMaterial::sTerrainMaterialName) == 0) {
        matId = static_cast<const TerrainMaterial*>(mat)->mMaterialId;
    }

    const auto& props = s_MaterialProps[matId < 65536 ? matId : 1];
    // Early exit if the material has no interaction triggers, except for ID 1
    if (!props.isFragile && !props.isTransformable && !props.spawnsParticles && !props.isInteractable && matId != 1) return;

    uint32_t terrainId = terrainBody.GetID().GetIndex();
    uint32_t subIdVal = subShapeId.GetValue();
    uint64_t blockKey = (static_cast<uint64_t>(terrainId) << 32) | subIdVal;
    uint32_t shardIdx = blockKey % NUM_SHARDS;
    auto& shard = s_Shards[shardIdx];

    // Lazy block position calculation
    bool posCalculated = false;
    JPH::RVec3 blockWorldPos;

    auto getBlockPos = [&]() -> JPH::RVec3 {
        if (posCalculated) return blockWorldPos;
        const JPH::Shape* baseShape = terrainBody.GetShape();
        if (baseShape && baseShape->GetSubType() == JPH::EShapeSubType::StaticCompound) {
            const JPH::StaticCompoundShape* compound = static_cast<const JPH::StaticCompoundShape*>(baseShape);
            JPH::SubShapeID remainder;
            JPH::uint32 idx = compound->GetSubShapeIndexFromID(subShapeId, remainder);
            if (idx < compound->GetNumSubShapes()) {
                blockWorldPos = terrainBody.GetCenterOfMassPosition() + terrainBody.GetRotation() * compound->GetSubShape(idx).GetPositionCOM();
                posCalculated = true;
                return blockWorldPos;
            }
        }
        posCalculated = true;
        blockWorldPos = manifold.GetWorldSpaceContactPointOn1(0); // Fallback
        return blockWorldPos;
    };

    int numPoints = manifold.mRelativeContactPointsOn1.size();
    for (int i = 0; i < numPoints; ++i) {
        JPH::RVec3 p = manifold.GetWorldSpaceContactPointOn1(i);
        JPH::Vec3 relVel = otherBody.GetPointVelocity(p) - terrainBody.GetPointVelocity(p);

        float impactSpeed = std::abs(relVel.Dot(manifold.mWorldSpaceNormal));
        // Calculate tangential sliding speed at this point
        float slidingSpeed = (relVel - relVel.Dot(manifold.mWorldSpaceNormal) * manifold.mWorldSpaceNormal).Length();

        float invMass = otherBody.GetMotionProperties() ? otherBody.GetMotionProperties()->GetInverseMass() : 0.0f;
        float otherMass = invMass > 0.0f ? 1.0f / invMass : s_Config.massBaseline;

        // Force and Energy estimates
        float gravity = ps ? std::abs(ps->GetGravity().GetY()) : 9.81f;
        float totalForce = (impactSpeed * otherMass) + (manifold.mPenetrationDepth * otherMass * gravity);

        auto populateEvent = [&](InteractionEvent& ev, InteractionType type, float strength) {
            ev.type = type;
            ev.materialId = matId;
            ev.subShapeId = subIdVal;
            ev.terrainBodyId = terrainId;
            JPH::RVec3 bPos = getBlockPos();
            ev.x1 = (float)bPos.GetX(); ev.y1 = (float)bPos.GetY(); ev.z1 = (float)bPos.GetZ();
            ev.strength = strength;
        };

        // 1. Zerstörung / Transformation (Deduped)
        if (props.isFragile && totalForce > props.breakThreshold) {
            std::lock_guard<std::mutex> lock(shard.mutex);
            if (shard.deduplicator.insert(blockKey).second) {
                if (s_TickBreaks.fetch_add(1, std::memory_order_relaxed) < s_Config.maxBreaksPerTick) {
                    InteractionEvent ev; populateEvent(ev, InteractionType::BLOCK_BREAK, totalForce);
                    QueueEvent(ev);
                }
            }
        } else if (props.isTransformable && (slidingSpeed > s_Config.transformMinSlidingSpeed || totalForce > s_Config.transformMinForce) && settings.mCombinedFriction > s_Config.transformMinFriction) {
            std::lock_guard<std::mutex> lock(shard.mutex);
            if (shard.deduplicator.insert(blockKey).second) {
                if (s_TickTransforms.fetch_add(1, std::memory_order_relaxed) < s_Config.maxTransformsPerTick) {
                    InteractionEvent ev; populateEvent(ev, InteractionType::BLOCK_TRANSFORM, totalForce);
                    QueueEvent(ev);
                }
            }
        }

        // 2. Interaktion (z.B. Türen)
        if (props.isInteractable && !isPersisted && totalForce > s_Config.interactMinForce) {
            InteractionEvent ev; populateEvent(ev, InteractionType::BLOCK_INTERACT, totalForce);
            JPH::Vec3 normal = manifold.mWorldSpaceNormal;
            ev.x2 = (float)normal.GetX(); ev.y2 = (float)normal.GetY(); ev.z2 = (float)normal.GetZ();
            QueueEvent(ev);
        }

        // 3. Partikel/Sound (Deduped)
        if (props.spawnsParticles) {
            // Thread-local RNG for high-performance random sampling without locks
            thread_local std::mt19937 tls_rng(std::random_device{}());
            thread_local std::uniform_real_distribution<float> tls_dist(0.0f, 1.0f);

            // Calculate relative velocity magnitude for general intensity
            float relVelMag = relVel.Length();

            // Hard cutoff: absolutely no effects if velocity is microscopic
            if (relVelMag < s_Config.particleMinVelocity) continue;

            // Shift mass scaling significantly:
            float massScale = (std::min)(s_Config.massMaxScale, (std::max)(s_Config.massMinScale, otherMass / s_Config.massBaseline));

            float energy = 0.0f;
            bool trigger = false;

            if (!isPersisted) {
                energy = ((relVelMag * 1.0f) + (manifold.mPenetrationDepth * 10.0f)) * massScale;
                trigger = (energy > s_Config.particleImpactEnergyThreshold); 
            } else if (relVelMag > s_Config.particleSlidingVelocityThreshold) {
                energy = (slidingSpeed * settings.mCombinedFriction + manifold.mPenetrationDepth * 5.0f) * massScale;
                if (energy > s_Config.particleSlidingEnergyThreshold) {
                    thread_local std::mt19937 rng(std::random_device{}());
                    thread_local std::uniform_real_distribution<float> dist(0.0f, 1.0f);
                    if (dist(rng) < (std::min)(s_Config.particleSlidingChanceMax, energy * s_Config.particleSlidingChanceMult)) trigger = true;
                }
            }

            if (trigger) {
                std::lock_guard<std::mutex> lock(shard.mutex);
                if (shard.deduplicator.insert(blockKey ^ 0xFAFBF000).second) {
                    if (s_TickParticles.fetch_add(1, std::memory_order_relaxed) < s_Config.maxParticlesPerTick) {
                        InteractionEvent ev; populateEvent(ev, InteractionType::PARTICLE_SLIDE, energy);
                        ev.x2 = (float)p.GetX(); ev.y2 = (float)p.GetY(); ev.z2 = (float)p.GetZ();
                        QueueEvent(ev);
                    }
                }
            }
        }
    }
}

/**
 * @brief Transfers asynchronous events back to Java.
 * 
 * This method drains all sharded queues into the provided destination buffer, 
 * resets the per-tick rate limiting budgets, and clears the spatial deduplicators.
 * 
 * @param outBuffer Target buffer for event data.
 * @param maxCount  Maximum number of events to flush.
 * @return The actual number of events written into the buffer.
 */
int TerrainInteraction::FlushEvents(InteractionEvent* outBuffer, int maxCount) {
    s_TickBreaks.store(0, std::memory_order_relaxed);
    s_TickTransforms.store(0, std::memory_order_relaxed);
    s_TickParticles.store(0, std::memory_order_relaxed);

    int total = 0;
    for (int i = 0; i < NUM_SHARDS; ++i) {
        auto& shard = s_Shards[i];
        {
            std::lock_guard<std::mutex> lock(shard.mutex);
            shard.deduplicator.clear();
        }
        {
            std::lock_guard<std::mutex> lock(shard.queueMutex);
            int toCopy = (std::min)((int)shard.queue.size(), maxCount - total);
            if (toCopy > 0) {
                std::memcpy(outBuffer + total, shard.queue.data(), (size_t)toCopy * sizeof(InteractionEvent));
                shard.queue.erase(shard.queue.begin(), shard.queue.begin() + toCopy);
                total += toCopy;
            }
        }
        if (total >= maxCount) break;
    }
    s_EventCount -= total;
    return total;
}

} // namespace Velthoric

extern "C" {

/**
 * @brief JNI Endpoint: Registers interaction-specific material data.
 */
JNIEXPORT void JNICALL
Java_net_xmx_velthoric_jni_TerrainInteraction_nRegisterInteractionMaterials(JNIEnv *env, jclass clazz, jobject buffer, jint count) {
    (void)clazz;
    if (!buffer || count <= 0) return;
    const Velthoric::TerrainInteraction::MaterialConfig* configs = static_cast<const Velthoric::TerrainInteraction::MaterialConfig*>(env->GetDirectBufferAddress(buffer));
    if (configs) {
        Velthoric::TerrainInteraction::RegisterMaterials(configs, count);
    }
}

/**
 * @brief JNI Endpoint: Flushes the interaction event queue into Java memory.
 */
JNIEXPORT jint JNICALL
Java_net_xmx_velthoric_jni_TerrainInteraction_nFlushEvents(JNIEnv *env, jclass clazz, jobject buffer, jint maxCount) {
    (void)env; (void)clazz;
    if (!buffer || maxCount <= 0) return 0;
    Velthoric::TerrainInteraction::InteractionEvent* outBuffer = static_cast<Velthoric::TerrainInteraction::InteractionEvent*>(env->GetDirectBufferAddress(buffer));
    if (outBuffer) {
        return Velthoric::TerrainInteraction::FlushEvents(outBuffer, maxCount);
    }
    return 0;
}

} // extern "C"