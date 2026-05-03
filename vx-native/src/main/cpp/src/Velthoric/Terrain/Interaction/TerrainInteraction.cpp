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
#include <algorithm>
#include <cmath>
#include <cstring>
#include <random>
#include <jni.h>

namespace Velthoric {

/** Static storage for material interaction properties. */
// Initialize global config with defaults
TerrainInteraction::GlobalConfig TerrainInteraction::s_Config;

TerrainInteraction::InternalMaterialProps TerrainInteraction::s_MaterialProps[65536];

/** Internal queue for interaction events (reserved for future batching logic). */
std::vector<TerrainInteraction::InteractionEvent> TerrainInteraction::s_EventQueue;
std::mutex TerrainInteraction::s_QueueMutex;
std::atomic<int> TerrainInteraction::s_EventCount{0};

// JNI Caching
static JavaVM* s_JavaVM = nullptr;
static jclass s_HandlerClass = nullptr;
static jmethodID s_BreakMethod = nullptr;
static jmethodID s_ParticleMethod = nullptr;
static jmethodID s_TransformMethod = nullptr;
static jmethodID s_InteractMethod = nullptr;

/**
 * @brief Bootstraps the interaction system's JNI bridge.
 * 
 * Locates the Java handler class and caches method IDs for high-frequency callbacks.
 * This should be called from a thread that is already attached to the JVM.
 */
void TerrainInteraction::InitJNI(JNIEnv* env) {
    if (s_JavaVM || !env) return;

    env->GetJavaVM(&s_JavaVM);
    jclass localClass = env->FindClass("net/xmx/velthoric/core/terrain/interaction/VxTerrainInteractionHandler");
    if (localClass) {
        s_HandlerClass = (jclass)env->NewGlobalRef(localClass);
        // Signature: (world, x, y, z, force)
        s_BreakMethod = env->GetStaticMethodID(s_HandlerClass, "onBlockBreak", "(Lnet/xmx/velthoric/core/physics/world/VxPhysicsWorld;DDDF)V");
        // Signature: (world, x, y, z, intensity)
        s_ParticleMethod = env->GetStaticMethodID(s_HandlerClass, "onSpawnParticles", "(Lnet/xmx/velthoric/core/physics/world/VxPhysicsWorld;FFFF)V");
        // Signature: (world, x, y, z, force)
        s_TransformMethod = env->GetStaticMethodID(s_HandlerClass, "onTerrainTransform", "(Lnet/xmx/velthoric/core/physics/world/VxPhysicsWorld;DDDF)V");
        // Signature: (world, x, y, z, nX, nY, nZ)
        s_InteractMethod = env->GetStaticMethodID(s_HandlerClass, "onBlockInteract", "(Lnet/xmx/velthoric/core/physics/world/VxPhysicsWorld;DDDFFF)V");
        
        // Ensure default material (ID 1) always spawns particles
        s_MaterialProps[1].spawnsParticles = true;
    }
}

/**
 * @brief Helper to retrieve the current JNI environment.
 * 
 * Attaches the current thread to the JVM if it's not already attached.
 */
static JNIEnv* GetJNIEnv() {
    if (!s_JavaVM) return nullptr;
    JNIEnv* env;
    jint res = s_JavaVM->GetEnv((void**)&env, JNI_VERSION_1_6);
    if (res == JNI_EDETACHED) {
        s_JavaVM->AttachCurrentThread((void**)&env, nullptr);
    }
    return env;
}

/**
 * @brief Updates the native lookup table for material interaction properties.
 */
void TerrainInteraction::RegisterMaterials(const MaterialConfig* configs, int count) {
    for (int i = 0; i < count; ++i) {
        uint32_t id = configs[i].matId;
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
    if (!world || terrainBody.GetShape() == nullptr) return;

    // 1. Resolve material from the Jolt shape
    const JPH::PhysicsMaterial* mat = terrainBody.GetShape()->GetMaterial(subShapeId);
    uint32_t matId = 1; // Default fallback to ID 1 (Standard Soil/Particles)

    if (mat && mat->GetDebugName() && strcmp(mat->GetDebugName(), TerrainMaterial::sTerrainMaterialName) == 0) {
        const TerrainMaterial* tMat = static_cast<const TerrainMaterial*>(mat);
        matId = tMat->mMaterialId;
    }

    const auto& props = s_MaterialProps[matId < 65536 ? matId : 1];
    // Early exit if the material has no interaction triggers, except for ID 1
    if (!props.isFragile && !props.isTransformable && !props.spawnsParticles && !props.isInteractable && matId != 1) return;

    // 2. Dispatch JNI Callbacks for all contact points
    JNIEnv* env = GetJNIEnv();
    if (!env || !s_HandlerClass) return;

    int numPoints = manifold.mRelativeContactPointsOn1.size();
    for (int i = 0; i < numPoints; ++i) {
        // Get world space contact point
        JPH::RVec3 p = manifold.GetWorldSpaceContactPointOn1(i);
        
        // 3. Physical interaction calculations at this point
        JPH::Vec3 relVel = otherBody.GetPointVelocity(p) - terrainBody.GetPointVelocity(p);
        
        float impactSpeed = std::abs(relVel.Dot(manifold.mWorldSpaceNormal));
        // Calculate tangential sliding speed at this point
        float slidingSpeed = (relVel - relVel.Dot(manifold.mWorldSpaceNormal) * manifold.mWorldSpaceNormal).Length();
        
        float invMass = otherBody.GetMotionProperties() ? otherBody.GetMotionProperties()->GetInverseMass() : 0.0f;
        float otherMass = invMass > 0.0f ? 1.0f / invMass : s_Config.massBaseline;
        
        // Force and Energy estimates
        float gravity = ps ? std::abs(ps->GetGravity().GetY()) : 9.81f;
        float staticPressure = manifold.mPenetrationDepth * otherMass * gravity; 
        float totalForceEstimate = (impactSpeed * otherMass) + staticPressure;

        // Handle Block Destruction (Fragile blocks)
        if (props.isFragile && totalForceEstimate > props.breakThreshold) {
            if (s_BreakMethod) {
                JPH::RVec3 insideP = p - manifold.mWorldSpaceNormal * 0.05f;
                env->CallStaticVoidMethod(s_HandlerClass, s_BreakMethod, world, 
                                        (double)insideP.GetX(), (double)insideP.GetY(), (double)insideP.GetZ(), 
                                        totalForceEstimate);
            }
        }

        // Handle Terrain Transformation (Soil wear-down)
        if (props.isTransformable && (slidingSpeed > s_Config.transformMinSlidingSpeed || totalForceEstimate > s_Config.transformMinForce) && settings.mCombinedFriction > s_Config.transformMinFriction) {
            if (s_TransformMethod) {
                JPH::RVec3 insideP = p - manifold.mWorldSpaceNormal * 0.05f;
                env->CallStaticVoidMethod(s_HandlerClass, s_TransformMethod, world, 
                                        (double)insideP.GetX(), (double)insideP.GetY(), (double)insideP.GetZ(),
                                        totalForceEstimate);
            }
        }

        // Handle Physical Interaction (Doors, Trapdoors, Fence Gates)
        if (props.isInteractable && !isPersisted && totalForceEstimate > s_Config.interactMinForce) {
            if (s_InteractMethod) {
                // Nudge inside the block slightly to ensure correct block lookup
                JPH::RVec3 insideP = p - manifold.mWorldSpaceNormal * 0.05f;

                JPH::Vec3 normal = manifold.mWorldSpaceNormal;
                env->CallStaticVoidMethod(s_HandlerClass, s_InteractMethod, world, 
                                        (double)insideP.GetX(), (double)insideP.GetY(), (double)insideP.GetZ(),
                                        (float)normal.GetX(), (float)normal.GetY(), (float)normal.GetZ());
            }
        }

        // Handle Visual/Audio Effects (Particles & Sounds)
        if (props.spawnsParticles) {
            // Thread-local RNG for high-performance random sampling without locks
            thread_local std::mt19937 tls_rng(std::random_device{}());
            thread_local std::uniform_real_distribution<float> tls_dist(0.0f, 1.0f);

            // Calculate relative velocity magnitude for general intensity
            float relVelMag = relVel.Length();
            
            // Hard cutoff: absolutely no effects if velocity is microscopic
            if (relVelMag < s_Config.particleMinVelocity) continue;
            
            // Shift mass scaling significantly:
            float massScale = std::min(s_Config.massMaxScale, std::max(s_Config.massMinScale, otherMass / s_Config.massBaseline));
            
            float totalEnergy = 0.0f;
            bool shouldTrigger = false;

            if (!isPersisted) {
                totalEnergy = ((relVelMag * 1.0f) + (manifold.mPenetrationDepth * 10.0f)) * massScale;
                shouldTrigger = (totalEnergy > s_Config.particleImpactEnergyThreshold); 
            } else {
                if (relVelMag > s_Config.particleSlidingVelocityThreshold) {
                    float frictionEnergy = slidingSpeed * settings.mCombinedFriction * 1.0f;
                    float pressureEnergy = manifold.mPenetrationDepth * 5.0f; 
                    totalEnergy = (frictionEnergy + pressureEnergy) * massScale;
                    
                    if (totalEnergy > s_Config.particleSlidingEnergyThreshold) {
                        float chance = std::min(s_Config.particleSlidingChanceMax, totalEnergy * s_Config.particleSlidingChanceMult);
                        if (tls_dist(tls_rng) < chance) {
                            shouldTrigger = true;
                        }
                    }
                }
            }

            if (shouldTrigger && s_ParticleMethod) {
                env->CallStaticVoidMethod(s_HandlerClass, s_ParticleMethod, world, 
                                        (float)p.GetX(), (float)p.GetY(), (float)p.GetZ(), 
                                        totalEnergy);
            }
        }
    }
}

/**
 * @brief Transfers asynchronous events back to Java.
 */
int TerrainInteraction::FlushEvents(InteractionEvent* outBuffer, int maxCount) {
    std::lock_guard<std::mutex> lock(s_QueueMutex);
    int count = std::min((int)s_EventQueue.size(), maxCount);
    for (int i = 0; i < count; ++i) {
        outBuffer[i] = s_EventQueue[i];
    }
    s_EventQueue.erase(s_EventQueue.begin(), s_EventQueue.begin() + count);
    s_EventCount -= count;
    return count;
}

} // namespace Velthoric

extern "C" {

/**
 * @brief JNI Endpoint: Registers interaction-specific material data.
 */
JNIEXPORT void JNICALL
Java_net_xmx_velthoric_jni_TerrainInteraction_nRegisterInteractionMaterials(JNIEnv *env, jclass clazz, jobject buffer, jint count) {
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
    if (!buffer || maxCount <= 0) return 0;
    Velthoric::TerrainInteraction::InteractionEvent* outBuffer = static_cast<Velthoric::TerrainInteraction::InteractionEvent*>(env->GetDirectBufferAddress(buffer));
    if (outBuffer) {
        return Velthoric::TerrainInteraction::FlushEvents(outBuffer, maxCount);
    }
    return 0;
}

} // extern "C"