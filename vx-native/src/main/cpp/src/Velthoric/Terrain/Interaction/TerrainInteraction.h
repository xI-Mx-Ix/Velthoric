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
#include <array>
#include <atomic>
#include <jni.h>

JPH_NAMESPACE_BEGIN
class PhysicsSystem;
JPH_NAMESPACE_END

namespace Velthoric {

/**
 * @brief Zero-allocation handler for physical interactions with voxel terrain.
 * 
 * Optimized for massive-scale simulations (5000+ bodies). This class manages the 
 * bridge between high-frequency physics events and Java game logic.
 * 
 * Performance Architecture:
 * 1. Sharded Ringbuffers: Eliminates std::vector reallocations by using fixed-size buffers.
 * 2. Lossy Deduplication: Uses a hash-based frame-tag system to filter redundant events in O(1).
 * 3. Atomic Rate Limiting: Uses lock-free counters to enforce tick budgets for particles/breaks.
 */
class TerrainInteraction {
public:
    /**
     * @brief Global configuration parameters for the interaction system.
     */
    struct Config {
        float massBaseline;              ///< Reference mass for scaling impact intensity.
        float massMinScale;               ///< Lower bound for mass-based multipliers.
        float massMaxScale;               ///< Upper bound for mass-based multipliers.

        float transformMinForce;         ///< Force required to change terrain state.
        float transformMinSlidingSpeed;    ///< Speed required for friction-based wear.
        float transformMinFriction;        ///< Friction coefficient threshold for wear.

        float interactMinForce;           ///< Force required to nudge interactive objects (doors).

        float particleMinVelocity;           ///< Minimum speed to consider particle emission.
        float particleImpactEnergyThreshold;  ///< Energy threshold for impact visuals.
        float particleSlidingVelocityThreshold; ///< Speed threshold for sliding visuals.
        float particleSlidingEnergyThreshold; ///< Sustained energy threshold for sliding effects.
        float particleSlidingChanceMult;    ///< Chance multiplier for stochastic emission.
        float particleSlidingChanceMax;      ///< Probability cap for sliding particles.

        // Rate Limiting (Budgets per tick)
        int maxParticlesPerTick;   ///< Max particle events allowed per server tick.
        int maxTransformsPerTick;   ///< Max terrain transformations allowed per server tick.
        int maxBreaksPerTick;      ///< Max block destruction events allowed per server tick.
    };

    /**
     * @brief Material configuration for interaction behavior.
     * Maps to the Java MaterialConfig for JNI memory layout compatibility.
     */
    struct MaterialConfig {
        uint32_t materialId;   ///< Unique ID of the voxel material.
        bool isFragile;        ///< Whether the block breaks under force.
        bool isTransformable;  ///< Whether the block turns into dirt/dust.
        bool spawnsParticles;  ///< Whether friction produces visual effects.
        uint8_t padding1;      ///< Memory alignment padding.
        float breakThreshold;  ///< Force required to shatter the block.
        bool isInteractable;   ///< Whether the block supports generic interaction events.
        uint8_t padding2[3];   ///< Memory alignment padding.
        float interactThreshold; ///< Force required to trigger generic interaction events.
    };

    /**
     * @brief Types of interactions reported back to Java.
     */
    enum class InteractionType : uint32_t {
        PARTICLE_SLIDE = 0,   ///< Visual particles from friction.
        BLOCK_BREAK = 1,      ///< Structural destruction.
        BLOCK_TRANSFORM = 2,  ///< State change (e.g., Grass -> Dirt).
        BLOCK_INTERACT = 3    ///< Logic trigger (e.g., Door Nudge).
    };

    /**
     * @brief Represents a single interaction event.
     * Fixed size of 48 bytes for cache-friendly transfers.
     */
    struct InteractionEvent {
        InteractionType type;   ///< Interaction category.
        uint32_t materialId;    ///< Source material ID.
        float x1, y1, z1;       ///< Event position (World Space).
        float x2, y2, z2;       ///< Secondary vector (Normal or point).
        float strength;         ///< Intensity/Force of the impact.
        uint32_t subShapeId;    ///< Jolt sub-shape index.
        uint32_t terrainBodyId; ///< Source body ID.
        uint32_t padding;       ///< Alignment padding.
    };

    /// Global configuration instance.
    static Config s_Config;

    /** @brief Updates the interaction configuration. */
    static void SetConfig(const Config& config) { s_Config = config; }

    /** @brief Registers material behavior data. */
    static void RegisterMaterials(const MaterialConfig* configs, int count);

    /** @brief Bootstraps JNI references. */
    static void InitJNI(JNIEnv* env);

    /**
     * @brief Processes a physics contact and triggers interaction logic.
     * 
     * @param world Global Java world reference.
     * @param ps Jolt Physics System.
     * @param terrainBody The terrain body involved.
     * @param otherBody The impacting body.
     * @param subShapeId The specific block sub-shape.
     * @param manifold Collision manifold data.
     * @param settings Collision settings.
     * @param terrainIsBody1 Manifold ordering flag.
     * @param isPersisted Whether this contact is continuing from last frame.
     * @param materialId Cached material ID from handler (0 if unknown).
     */
    static void ProcessInteraction(jobject world, const JPH::PhysicsSystem* ps, const JPH::Body& terrainBody, const JPH::Body& otherBody, JPH::SubShapeID subShapeId, const JPH::ContactManifold& manifold, JPH::ContactSettings& settings, bool terrainIsBody1, bool isPersisted, uint32_t materialId = 0);

    /**
     * @brief Flushes the native event queue into a Java DirectBuffer.
     * 
     * @param outBuffer Destination buffer.
     * @param maxCount Max events to transfer.
     * @return int Number of events written.
     */
    static int FlushEvents(InteractionEvent* outBuffer, int maxCount);

private:
    /** @brief Internal helper to push events into the sharded buffers. */
    static void QueueEvent(const InteractionEvent& event);

    /** @brief Internal representation of material properties. */
    struct InternalMaterialProps {
        bool isFragile = false;
        bool isTransformable = false;
        bool spawnsParticles = false;
        bool isInteractable = false;
        float breakThreshold = 0.0f;
        float interactThreshold = 50.0f;
    };

    /// Fast lookup table for material properties (65536 slots).
    static std::array<InternalMaterialProps, 65536> s_MaterialProps;
    
    /// Cached JNI handler class.
    static jclass s_HandlerClass;

    /// Global atomic counters for rate limiting.
    static std::atomic<int> s_EventCount;
    static std::atomic<int> s_TickBreaks;
    static std::atomic<int> s_TickTransforms;
    static std::atomic<int> s_TickParticles;

    /**
     * @brief Number of shards to minimize lock contention.
     */
    static constexpr int NUM_SHARDS = 32;

    /** @brief Size of the fixed-size event ringbuffer per shard. */
    static constexpr int QUEUE_SIZE_PER_SHARD = 512;

    /** @brief Size of the lossy deduplication table per shard. */
    static constexpr int DEDUP_SIZE_PER_SHARD = 1024;

    /**
     * @brief A sharded event buffer.
     * Aligned to 64 bytes to prevent "False Sharing" between physics worker threads.
     */
    struct alignas(64) Shard {
        std::atomic_flag lock = ATOMIC_FLAG_INIT; ///< Atomic spinlock.
        
        /// Lossy Deduplicator: Stores keys to prevent redundant events in the same frame.
        std::array<uint64_t, DEDUP_SIZE_PER_SHARD> dedupTable = {};
        
        /// Fixed-size Ringbuffer for InteractionEvents.
        std::array<InteractionEvent, QUEUE_SIZE_PER_SHARD> queue = {};
        uint32_t head = 0; ///< Read pointer.
        uint32_t tail = 0; ///< Write pointer.

        /// Spin-locks the shard.
        inline void Lock() { while (lock.test_and_set(std::memory_order_acquire)); }
        /// Unlocks the shard.
        inline void Unlock() { lock.clear(std::memory_order_release); }
        
        /**
         * @brief Checks if an event key is already in the table for this tick.
         * 
         * @param key Unique event key.
         * @return true if the event is NEW and should be queued.
         * @return false if the event is DEDUPLICATED.
         */
        bool TryDeduplicate(uint64_t key) {
            uint32_t idx = key % DEDUP_SIZE_PER_SHARD;
            if (dedupTable[idx] == key) return false;
            dedupTable[idx] = key;
            return true;
        }

        /**
         * @brief Pushes an event into the ringbuffer.
         * 
         * @param ev The event data.
         * @return true on success, false if the buffer is FULL.
         */
        bool Enqueue(const InteractionEvent& ev) {
            uint32_t next = (tail + 1) % QUEUE_SIZE_PER_SHARD;
            if (next == head) return false; // Overflow (Load Shedding)
            queue[tail] = ev;
            tail = next;
            return true;
        }
    };

    /// Sharded storage instances.
    static Shard s_Shards[NUM_SHARDS];
};

} // namespace Velthoric