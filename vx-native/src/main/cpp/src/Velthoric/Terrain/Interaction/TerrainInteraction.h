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
#include <jni.h>

namespace Velthoric {

/**
 * @brief High-performance native terrain interaction subsystem.
 * 
 * The TerrainInteraction class manages physical feedback mechanisms triggered by terrain collisions.
 * This includes:
 * - Dynamic block destruction (fragile blocks like glass/ice).
 * - Real-time terrain transformation (vegetation/soil wear-down).
 * - Friction-based particle effects.
 * 
 * To ensure maximum performance during the physics simulation step, this class operates primarily 
 * on native data structures and executes JNI callbacks directly from the physics thread 
 * using cached method identifiers.
 */
class TerrainInteraction {
public:
    /**
     * @brief Internal classification for different types of terrain feedback.
     */
    enum class InteractionType : uint32_t {
        PARTICLE_SLIDE = 0,   ///< Triggers visual particles based on sliding friction.
        BLOCK_BREAK = 1,      ///< Triggers destruction of fragile blocks (Ice, Glass, Leaves).
        BLOCK_TRANSFORM = 2   ///< Triggers terrain state modification (e.g., Grass to Dirt).
    };

    /**
     * @brief Represents a single interaction event queued for processing.
     */
    struct InteractionEvent {
        InteractionType type; ///< Type of interaction.
        uint32_t materialId;  ///< Material ID of the terrain block.
        float x, y, z;        ///< World-space coordinates of the contact.
        float strength;       ///< Physical intensity (force/speed) of the event.
        uint32_t subShapeId;  ///< Jolt sub-shape ID for precise block identification.
        uint32_t terrainBodyId; ///< ID of the terrain body involved.
    };

    /**
     * @brief Configuration for a specific material's interaction behavior.
     * Maps 1:1 to the Java-side MaterialConfig for JNI transfer.
     */
    struct MaterialConfig {
        uint32_t matId;       ///< Unique material ID.
        bool isFragile;       ///< Can be broken by force.
        bool isTransformable; ///< Can be worn down into dirt.
        bool spawnsParticles; ///< Produces friction particles.
        float breakThreshold; ///< Force/Pressure threshold required for breaking.
    };

    /**
     * @brief Updates the native material interaction registry.
     * 
     * @param configs Pointer to an array of material configurations.
     * @param count   Number of configurations in the array.
     */
    static void RegisterMaterials(const MaterialConfig* configs, int count);

    /**
     * @brief Initializes JNI method and class identifiers for callbacks.
     * 
     * Must be called once before any interactions occur, typically when the 
     * physics world is initialized.
     * 
     * @param env The JNI environment pointer.
     */
    static void InitJNI(JNIEnv* env);

    /**
     * @brief Processes a contact event and triggers appropriate interaction callbacks.
     * 
     * This method evaluates the physical parameters of a collision (impact speed, 
     * sliding speed, friction) against the registered material properties.
     * 
     * @param world            Global reference to the Java VxPhysicsWorld object.
     * @param terrainBody      The terrain body involved in the collision.
     * @param otherBody        The body colliding with the terrain.
     * @param terrainSubShapeId Precise identification of the block within the terrain.
     * @param manifold         Contact manifold containing geometric information.
     * @param settings         Contact settings for friction/restitution.
     * @param isPersisted      True if this contact is a continuation of a previous one.
     */
    static void ProcessInteraction(jobject world,
                                 const JPH::Body& terrainBody, const JPH::Body& otherBody, 
                                 JPH::SubShapeID terrainSubShapeId,
                                 const JPH::ContactManifold& manifold, 
                                 const JPH::ContactSettings& settings, 
                                 bool isPersisted);

    /**
     * @brief Synchronously flushes the internal event queue into a provided buffer.
     * 
     * Used by the JNI layer to transfer batched events back to Java.
     * 
     * @param outBuffer Target buffer for event data.
     * @param maxCount  Maximum number of events to flush.
     * @return The actual number of events written.
     */
    static int FlushEvents(InteractionEvent* outBuffer, int maxCount);

private:
    /** @brief Internal representation of material properties for O(1) lookups. */
    struct InternalMaterialProps {
        bool isFragile = false;
        bool isTransformable = false;
        bool spawnsParticles = false;
        float breakThreshold = 5000.0f;
    };

    /** Fixed-size array for fast material property lookup by ID. */
    static InternalMaterialProps s_MaterialProps[65536];
    
    /** Queue for asynchronous events (if batching is used). */
    static std::vector<InteractionEvent> s_EventQueue;
    static std::mutex s_QueueMutex;
    static std::atomic<int> s_EventCount;
};

} // namespace Velthoric