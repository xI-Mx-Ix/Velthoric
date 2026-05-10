/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 *
 * Author: xI-Mx-Ix
 */
 #pragma once

#include <Jolt/Jolt.h>
#include <Jolt/Physics/PhysicsSystem.h>
#include <Jolt/Physics/Body/BodyInterface.h>
#include "Velthoric/Terrain/TerrainSystem.h"
#include <unordered_set>
#include <vector>

namespace Velthoric {

/**
 * @struct TerrainTrackerResult
 * @brief Data structure holding bit-packed chunk coordinates that require Java-side processing.
 * 
 * This structure is returned by the TerrainTracker's update loop and serves as the communication
 * bridge back to the Java layer. It categorizes chunks based on the type of data submission they require.
 */
struct TerrainTrackerResult {
    /**
     * @brief A collection of bit-packed chunk section coordinates that are completely new to the simulation.
     * 
     * These chunks have just been requested due to bodies moving near them, and they require a full,
     * initial voxel snapshot generation from the Java Minecraft world to build their collision shape.
     */
    std::vector<int64_t> initialBuildChunks;

    /**
     * @brief A collection of bit-packed chunk section coordinates that are already active but require an update.
     * 
     * These chunks typically have placeholder geometry or outdated states, and the tracking system has
     * flagged them for a prioritized data resubmission from the Java Minecraft world to ensure precise collision.
     */
    std::vector<int64_t> updateBuildChunks;
};

/**
 * @class TerrainTracker
 * @brief High-performance, physics-driven native terrain tracking engine.
 * 
 * The TerrainTracker iterates over all active bodies in the Jolt Physics simulation, clusters them spatially,
 * and dynamically projects their future trajectories based on velocity. It then orchestrates the 
 * Velthoric::TerrainSystem to seamlessly load, activate, or release static terrain chunks exactly where they are needed.
 * By operating natively, it entirely eliminates the severe overhead associated with JNI body state synchronization.
 */
class TerrainTracker {
public:
    /**
     * @brief Instantiates the TerrainTracker with references to the core physics structures.
     * 
     * @param physicsSystem Pointer to the primary Jolt PhysicsSystem, used to traverse all bodies.
     * @param terrainSystem Pointer to the Velthoric TerrainSystem, used to issue chunk state commands.
     * @param terrainLayer  The 16-bit Jolt ObjectLayer identifier that signifies static terrain bodies.
     *                      Bodies on this layer are strictly ignored during velocity tracking.
     */
    TerrainTracker(JPH::PhysicsSystem* physicsSystem, TerrainSystem* terrainSystem, uint16_t terrainLayer);
    
    /**
     * @brief Executes the comprehensive physics clustering and chunk allocation cycle.
     * 
     * This method accesses Jolt's BodyInterface without locking to read transforms, AABBs, and velocities
     * of all non-terrain bodies. It maps these bodies into coarse 3D grid cells, projects their future AABBs
     * across time (e.g., 0.5 seconds), and calculates the exact set of Minecraft chunk sections that must be present.
     * It actively instructs the TerrainSystem to Request, Release, Activate, and Deactivate specific chunk coordinates.
     * 
     * @return TerrainTrackerResult containing arrays of newly requested or prioritized chunk positions.
     */
    TerrainTrackerResult Update();

    /**
     * @brief Evicts all tracking state and unconditionally releases all active terrain holds.
     * 
     * This method loops through all internally cached `m_PreviouslyRequiredChunks` and explicitly commands 
     * the TerrainSystem to release them. It also surveys the TerrainSystem for any active chunk positions
     * and forcibly deactivates them, effectively wiping the physics world clean of dynamic terrain footprints.
     */
    void Clear();

private:
    /**
     * @brief Inflates a given bounding box by a chunk radius and yields all intersecting chunk section coordinates.
     * 
     * This mathematical utility transforms world-space constraints into discretely packed 64-bit Minecraft
     * chunk section coordinates. It includes a critical 'Safety Brake' mechanism that calculates the volume 
     * of the requested sections and aborts the operation if it exceeds a sane threshold, thereby protecting 
     * the engine from infinite loops or out-of-memory errors caused by NaN/Infinity physics glitches.
     * 
     * @param minX The minimum world-space X coordinate of the target box.
     * @param minY The minimum world-space Y coordinate of the target box.
     * @param minZ The minimum world-space Z coordinate of the target box.
     * @param maxX The maximum world-space X coordinate of the target box.
     * @param maxY The maximum world-space Y coordinate of the target box.
     * @param maxZ The maximum world-space Z coordinate of the target box.
     * @param radiusInChunks The padding radius, in chunks, to expand the search volume.
     * @param outChunks A reference to the unordered set where the bit-packed section coordinates will be inserted.
     */
    void ForEachSectionInBox(float minX, float minY, float minZ, float maxX, float maxY, float maxZ, int radiusInChunks, std::unordered_set<int64_t>& outChunks);
    
    /** 
     * @brief Pointer to the primary Jolt PhysicsSystem. 
     */
    JPH::PhysicsSystem* m_PhysicsSystem;

    /** 
     * @brief Pointer to the Velthoric TerrainSystem used for dispatching state changes. 
     */
    TerrainSystem* m_TerrainSystem;

    /** 
     * @brief The predefined Jolt ObjectLayer that identifies terrain geometries to skip them during tracking. 
     */
    uint16_t m_TerrainLayer;

    /** 
     * @brief State cache tracking the exact chunk coordinates requested during the previous tick. 
     * Used to calculate the diff required to release abandoned chunks.
     */
    std::unordered_set<int64_t> m_PreviouslyRequiredChunks;
};

} // namespace Velthoric