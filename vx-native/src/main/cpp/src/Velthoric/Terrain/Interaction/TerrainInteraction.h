/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 *
 * Author: xI-Mx-Ix
 */
#pragma once
#include <Jolt/Jolt.h>
#include <Jolt/Physics/Body/Body.h>
#include <Jolt/Physics/Collision/ContactListener.h>
#include <vector>
#include <mutex>
#include <atomic>
#include <unordered_set>
#include <jni.h>

JPH_NAMESPACE_BEGIN
class PhysicsSystem;
JPH_NAMESPACE_END

namespace Velthoric {

/**
 * @brief High-performance handler for physical interactions with voxel terrain.
 * 
 * This class manages the bridge between native Jolt physics events and Java-side 
 * game logic. It handles:
 * - Block destruction based on impact force.
 * - Material-based terrain transformations.
 * - Friction-based particle effects.
 * 
 * To ensure maximum performance during the physics simulation step, this class operates primarily 
 * on native data structures. Interaction events are queued in a thread-safe native buffer 
 * and flushed to Java once per tick to eliminate JNI overhead and prevent thread contention.
 */
class TerrainInteraction {
public:
    /**
     * @brief Configuration parameters for the interaction system.
     */
    struct Config {
        float massBaseline = 100.0f;              ///< Reference mass (kg) for scaling impact intensity.
        float massMinScale = 0.1f;               ///< Minimum multiplier for mass-based force scaling.
        float massMaxScale = 2.0f;               ///< Maximum multiplier for mass-based force scaling.

        float transformMinForce = 200.0f;         ///< Minimum force required to trigger block transformation.
        float transformMinSlidingSpeed = 1.0f;    ///< Minimum speed (m/s) for friction-based transformation.
        float transformMinFriction = 0.3f;        ///< Minimum friction coefficient required for transformation.

        float interactMinForce = 50.0f;           ///< Minimum force required to trigger block interaction (doors/gates).

        float particleMinVelocity = 0.05f;           ///< Minimum relative velocity to consider any particle effects.
        float particleImpactEnergyThreshold = 1.0f;  ///< Energy threshold for spawning impact particles.
        float particleSlidingVelocityThreshold = 0.5f; ///< Minimum speed for sustained sliding particles.
        float particleSlidingEnergyThreshold = 0.05f; ///< Minimum energy density for sliding particles.
        float particleSlidingChanceMult = 0.005f;    ///< Probability multiplier for sliding particle emission.
        float particleSlidingChanceMax = 0.05f;      ///< Upper bound for sliding particle emission probability.

        // Rate Limiting (Budgets per tick)
        int maxParticlesPerTick = 128;   ///< Max sliding particle events per tick.
        int maxTransformsPerTick = 64;   ///< Max terrain transformations per tick.
        int maxBreaksPerTick = 256;      ///< Max block breaks per tick.
    };

    /**
     * @brief Material configuration for interaction behavior.
     * Maps 1:1 to the Java-side MaterialConfig for JNI transfer (16 bytes).
     */
    struct MaterialConfig {
        uint32_t materialId;   ///< Unique material ID.
        bool isFragile;        ///< Can be broken by force.
        bool isTransformable;  ///< Can be worn down into dirt.
        bool spawnsParticles;  ///< Produces friction particles.
        uint8_t padding1;      ///< Alignment padding.
        float breakThreshold;  ///< Force threshold for breaking.
        bool isInteractable;   ///< Can be physically nudged.
        uint8_t padding2[3];   ///< Alignment padding.
    };

    /**
     * @brief Types of interactions that can be triggered.
     */
    enum class InteractionType : uint32_t {
        PARTICLE_SLIDE = 0,   ///< Triggers visual particles based on sliding friction.
        BLOCK_BREAK = 1,      ///< Triggers destruction of fragile blocks (Ice, Glass, Leaves).
        BLOCK_TRANSFORM = 2,  ///< Triggers terrain state modification (e.g., Grass to Dirt).
        BLOCK_INTERACT = 3    ///< Triggers physical interaction with blocks (Doors, Gates).
    };
 
    /**
     * @brief Represents a single interaction event queued for processing.
     * Total size: 48 bytes (aligned).
     */
    struct InteractionEvent {
        InteractionType type; ///< Type of interaction.
        uint32_t materialId;  ///< Material ID of the terrain block.
        float x1, y1, z1;     ///< Position 1 (usually block center).
        float x2, y2, z2;     ///< Position 2 (usually contact point or normal).
        float strength;       ///< Physical intensity (force/speed) of the event.
        uint32_t subShapeId;  ///< Jolt sub-shape ID for precise block identification.
        uint32_t terrainBodyId; ///< ID of the terrain body involved.
        uint32_t padding;     ///< Alignment padding.
    };

    /**
     * @brief Global configuration instance.
     */
    static Config s_Config;

    /**
     * @brief Updates the global interaction configuration.
     */
    static void SetConfig(const Config& config) { s_Config = config; }

    /**
     * @brief Registers material properties for fast lookup.
     * 
     * @param configs Array of material configurations.
     * @param count Number of configurations in the array.
     */
    static void RegisterMaterials(const MaterialConfig* configs, int count);

    /**
     * @brief Initializes JNI class identifiers for the interaction system.
     * 
     * Must be called once before any interactions occur, typically when the 
     * physics world is initialized.
     * 
     * @param env The JNI Environment pointer.
     */
    static void InitJNI(JNIEnv* env);

    /**
     * @brief Core processing logic for physical contact.
     * 
     * Analyzes a contact manifold between a terrain body and another object, 
     * calculates forces and velocities, and queues interaction events.
     * 
     * @param world Global world object reference.
     * @param ps The Jolt physics system.
     * @param terrainBody The body identified as terrain.
     * @param otherBody The other body in the contact.
     * @param subShapeId The specific sub-shape ID of the terrain.
     * @param manifold The contact manifold data.
     * @param settings The contact settings (friction/restitution).
     * @param terrainIsBody1 Whether the terrain body is the first body in the manifold.
     * @param isPersisted Whether this is a new or persisting contact.
     */
    static void ProcessInteraction(jobject world, const JPH::PhysicsSystem* ps, const JPH::Body& terrainBody, const JPH::Body& otherBody, JPH::SubShapeID subShapeId, const JPH::ContactManifold& manifold, JPH::ContactSettings& settings, bool terrainIsBody1, bool isPersisted);

    /**
     * @brief Transfers asynchronous events back to Java.
     * 
     * @param outBuffer Destination buffer for event data.
     * @param maxCount Maximum number of events to transfer.
     * @return The number of events actually transferred.
     */
    static int FlushEvents(InteractionEvent* outBuffer, int maxCount);

private:
    /** @brief Internal helper to push events into the thread-safe queue. */
    static void QueueEvent(const InteractionEvent& event);

    /** @brief Internal representation of material properties for O(1) lookups. */
    struct InternalMaterialProps {
        bool isFragile = false;
        bool isTransformable = false;
        bool spawnsParticles = false;
        bool isInteractable = false;
        float breakThreshold = 0.0f;
    };

    static std::vector<InternalMaterialProps> s_MaterialProps;
    static jclass s_HandlerClass;
    static jmethodID s_BreakMethod;
    static jmethodID s_TransformMethod;
    static jmethodID s_ParticleMethod;
    static jmethodID s_InteractMethod;

    static std::vector<InteractionEvent> s_EventQueue;
    static std::mutex s_QueueMutex;
    static std::atomic<int> s_EventCount;

    /** Rate limiting counters (reset per tick in FlushEvents). */
    static std::atomic<int> s_TickBreaks;
    static std::atomic<int> s_TickTransforms;
    static std::atomic<int> s_TickParticles;

    /** 
     * Sharded spatial deduplication to eliminate lock contention.
     */
    static constexpr int NUM_SHARDS = 16;
    struct Shard {
        std::mutex mutex;
        std::unordered_set<uint64_t> deduplicator;
        std::mutex queueMutex;
        std::vector<InteractionEvent> queue;
    };
    static Shard s_Shards[NUM_SHARDS];
};

} // namespace Velthoric