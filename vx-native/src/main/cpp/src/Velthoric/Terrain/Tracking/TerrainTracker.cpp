/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 *
 * Author: xI-Mx-Ix
 */
#include "Velthoric/Terrain/Tracking/TerrainTracker.h"
#include <cmath>
#include <iostream>
#include <unordered_map>
#include <algorithm>
#include <jni.h>

namespace Velthoric {

/**
 * @brief The grid size in chunks used to spatially cluster bodies.
 * Groups physics bodies into 4x4x4 chunk grids to optimize AABB bounds calculation.
 */
const int GRID_CELL_SIZE_IN_CHUNKS = 4;

/**
 * @brief The radius, measured in chunks, by which a moving body's bounding box is expanded
 * for keeping terrain chunks actively loaded in the physics simulation.
 */
const int ACTIVATION_RADIUS_CHUNKS = 1;

/**
 * @brief The radius, measured in chunks, used to proactively generate and preload terrain 
 * around the clustered bodies.
 */
const int PRELOAD_RADIUS_CHUNKS = 3;

/**
 * @brief The scalar time (in seconds) used to project the future trajectory of bodies.
 * The tracking system extrapolates current velocity forward by this duration to preload chunks.
 */
const float PREDICTION_SECONDS = 0.5f;

/**
 * @brief The absolute maximum linear distance (in blocks) a trajectory prediction is allowed to reach.
 * This effectively caps the look-ahead to prevent astronomical bounds scaling for fast bodies.
 */
const float MAX_PREDICTION_DISTANCE = 64.0f;

/**
 * @brief The maximum volume of chunks (width * height * depth) that can be requested 
 * by a single cluster iteration. Acts as a strict safety brake against memory exhaustion.
 */
const int64_t MAX_CHUNKS_PER_CLUSTER_ITERATION = 20000;

/**
 * @brief The maximum world Y-axis coordinate (height) where terrain generation is tracked.
 * Bodies soaring above this ceiling will safely ignore terrain generation below.
 */
const float MAX_GENERATION_HEIGHT = 500.0f;

/**
 * @brief The minimum world Y-axis coordinate (depth) where terrain generation is tracked.
 * Bodies plunging below this floor will no longer trigger chunk loading.
 */
const float MIN_GENERATION_HEIGHT = -250.0f;

/**
 * @brief Helper utility to convert a continuous floating-point world coordinate into a discrete chunk section coordinate.
 * @param pos The 1D world-space coordinate (X, Y, or Z).
 * @return The corresponding 1D chunk section coordinate, equivalent to floor(pos / 16.0).
 */
inline int64_t BlockToSectionCoord(float pos) {
    return static_cast<int64_t>(std::floor(pos / 16.0f));
}

/**
 * @brief Helper utility that packs three discrete section coordinates (X, Y, Z) into a single 64-bit integer.
 * This mirrors exactly the net.minecraft.core.SectionPos.asLong(x, y, z) mechanism.
 * @param x The chunk section X coordinate.
 * @param y The chunk section Y coordinate.
 * @param z The chunk section Z coordinate.
 * @return A mathematically packed 64-bit identifier uniquely representing the chunk section.
 */
inline int64_t SectionAsLong(int64_t x, int64_t y, int64_t z) {
    return ((x & 0x3FFFFF) << 42) | (y & 0xFFFFF) | ((z & 0x3FFFFF) << 20);
}

/**
 * @brief Constructor for the TerrainTracker subsystem.
 * @param physicsSystem Direct pointer to the main Jolt physics context.
 * @param terrainSystem Direct pointer to the Velthoric terrain chunk manager.
 * @param terrainLayer The designated Jolt ObjectLayer for terrain geometry.
 */
TerrainTracker::TerrainTracker(JPH::PhysicsSystem* physicsSystem, TerrainSystem* terrainSystem, uint16_t terrainLayer)
    : m_PhysicsSystem(physicsSystem), m_TerrainSystem(terrainSystem), m_TerrainLayer(terrainLayer) {
}

/**
 * @brief Executes the comprehensive physics clustering and chunk allocation cycle.
 * 
 * This method is the beating heart of the native terrain tracking system. It follows a multi-stage execution pipeline:
 * 1. Queries all bodies currently registered in Jolt's simulation.
 * 2. Filters out terrain geometries and dormant/out-of-bounds objects.
 * 3. Maps valid bodies into coarse spatial grids (`cachedBodyClusters`).
 * 4. Extrapolates the collective bounding boxes of each cluster utilizing their linear velocities.
 * 5. Iterates across these expanded bounds to isolate discrete chunk section coordinates (`currentlyRequiredChunks`).
 * 6. Dispatches 'Request' and 'Release' directives to the TerrainSystem to govern the chunk lifecycle.
 * 7. Scans active bodies explicitly for fine-grained chunk 'Activation' and 'Deactivation'.
 * 
 * @return TerrainTrackerResult Struct containing dynamically populated arrays of chunks needing generation or updates.
 */
TerrainTrackerResult TerrainTracker::Update() {
    TerrainTrackerResult result;
    if (!m_PhysicsSystem || !m_TerrainSystem) return result;

    JPH::BodyInterface& bodyInterface = m_PhysicsSystem->GetBodyInterfaceNoLock();
    JPH::BodyIDVector bodies;
    m_PhysicsSystem->GetBodies(bodies);

    std::unordered_map<int64_t, std::vector<JPH::BodyID>> cachedBodyClusters;
    std::unordered_set<int64_t> currentlyRequiredChunks;
    std::unordered_set<int64_t> requiredActiveSet;

    // 1. Cluster bodies: Assign each valid physics body into a coarse Grid Hash Map
    for (JPH::BodyID id : bodies) {
        if (!bodyInterface.IsAdded(id)) continue;
        if (bodyInterface.GetObjectLayer(id) == m_TerrainLayer) continue;

        JPH::RVec3 pos = bodyInterface.GetPosition(id);
        float px = pos.GetX();
        float py = pos.GetY();
        float pz = pos.GetZ();

        // Evade mathematical singularities that could violently destabilize the spatial hash
        if (!std::isfinite(px) || !std::isfinite(py) || !std::isfinite(pz)) continue;
        
        // Exclude bodies orbiting out of structural relevance
        if (py > MAX_GENERATION_HEIGHT || py < MIN_GENERATION_HEIGHT) continue;

        int64_t cellX = BlockToSectionCoord(px) / GRID_CELL_SIZE_IN_CHUNKS;
        int64_t cellY = BlockToSectionCoord(py) / GRID_CELL_SIZE_IN_CHUNKS;
        int64_t cellZ = BlockToSectionCoord(pz) / GRID_CELL_SIZE_IN_CHUNKS;
        int64_t cellKey = SectionAsLong(cellX, cellY, cellZ);

        cachedBodyClusters[cellKey].push_back(id);
    }

    // Fast-path exit if no bodies require tracking
    if (cachedBodyClusters.empty()) {
        Clear();
        return result;
    }

    // 2. Calculate required preload set: Expand cluster AABBs based on velocity
    for (const auto& pair : cachedBodyClusters) {
        const auto& cluster = pair.second;
        if (cluster.empty()) continue;

        float minX = INFINITY, minY = INFINITY, minZ = INFINITY;
        float maxX = -INFINITY, maxY = -INFINITY, maxZ = -INFINITY;
        bool clusterHasValidBodies = false;

        for (JPH::BodyID id : cluster) {
            JPH::AABox bounds = bodyInterface.GetTransformedShape(id).GetWorldSpaceBounds();
            
            float bMinX = bounds.mMin.GetX();
            float bMinY = bounds.mMin.GetY();
            float bMinZ = bounds.mMin.GetZ();
            float bMaxX = bounds.mMax.GetX();
            float bMaxY = bounds.mMax.GetY();
            float bMaxZ = bounds.mMax.GetZ();

            // Ignore bodies with totally corrupted bounding boxes
            if (!std::isfinite(bMinX) || !std::isfinite(bMaxX)) continue;

            clusterHasValidBodies = true;

            // Expand the bounding domain for the overarching cluster
            minX = std::min(minX, bMinX);
            minY = std::min(minY, bMinY);
            minZ = std::min(minZ, bMinZ);
            maxX = std::max(maxX, bMaxX);
            maxY = std::max(maxY, bMaxY);
            maxZ = std::max(maxZ, bMaxZ);

            // Compute predictive velocity padding
            JPH::Vec3 vel = bodyInterface.GetLinearVelocity(id);
            float velX = vel.GetX();
            float velY = vel.GetY();
            float velZ = vel.GetZ();

            // If the body is moving, stretch its footprint towards its destination
            if (std::abs(velX) > 0.01f || std::abs(velY) > 0.01f || std::abs(velZ) > 0.01f) {
                float predictedOffsetX = velX * PREDICTION_SECONDS;
                float predictedOffsetY = velY * PREDICTION_SECONDS;
                float predictedOffsetZ = velZ * PREDICTION_SECONDS;

                // Restrict the maximum displacement projection
                predictedOffsetX = std::max(-MAX_PREDICTION_DISTANCE, std::min(MAX_PREDICTION_DISTANCE, predictedOffsetX));
                predictedOffsetY = std::max(-MAX_PREDICTION_DISTANCE, std::min(MAX_PREDICTION_DISTANCE, predictedOffsetY));
                predictedOffsetZ = std::max(-MAX_PREDICTION_DISTANCE, std::min(MAX_PREDICTION_DISTANCE, predictedOffsetZ));

                minX = std::min(minX, bMinX + predictedOffsetX);
                minY = std::min(minY, bMinY + predictedOffsetY);
                minZ = std::min(minZ, bMinZ + predictedOffsetZ);
                maxX = std::max(maxX, bMaxX + predictedOffsetX);
                maxY = std::max(maxY, bMaxY + predictedOffsetY);
                maxZ = std::max(maxZ, bMaxZ + predictedOffsetZ);
            }
        }

        if (clusterHasValidBodies) {
            ForEachSectionInBox(minX, minY, minZ, maxX, maxY, maxZ, PRELOAD_RADIUS_CHUNKS, currentlyRequiredChunks);
        }
    }

    // 3. Request new chunks that entered the required set
    for (int64_t packedPos : currentlyRequiredChunks) {
        if (m_PreviouslyRequiredChunks.find(packedPos) == m_PreviouslyRequiredChunks.end()) {
            bool isNew = m_TerrainSystem->RequestChunk(packedPos);
            if (isNew) {
                result.initialBuildChunks.push_back(packedPos);
            }
        }
    }

    // 4. Release old chunks that completely exited the required set
    for (int64_t packedPos : m_PreviouslyRequiredChunks) {
        if (currentlyRequiredChunks.find(packedPos) == currentlyRequiredChunks.end()) {
            m_TerrainSystem->ReleaseChunk(packedPos);
        }
    }

    m_PreviouslyRequiredChunks = currentlyRequiredChunks;

    // 5. Active chunks logic: Refine the exact chunks needed for active body collisions
    for (const auto& pair : cachedBodyClusters) {
        for (JPH::BodyID id : pair.second) {
            // Only strictly active bodies require physics terrain to be awake
            if (bodyInterface.IsActive(id)) {
                JPH::RVec3 pos = bodyInterface.GetPosition(id);
                float py = pos.GetY();
                if (py > MAX_GENERATION_HEIGHT || py < MIN_GENERATION_HEIGHT) continue;

                JPH::AABox bounds = bodyInterface.GetTransformedShape(id).GetWorldSpaceBounds();
                float minX = bounds.mMin.GetX();
                float minY = bounds.mMin.GetY();
                float minZ = bounds.mMin.GetZ();
                float maxX = bounds.mMax.GetX();
                float maxY = bounds.mMax.GetY();
                float maxZ = bounds.mMax.GetZ();

                if (std::isfinite(minX) && std::isfinite(maxX) &&
                    std::isfinite(minY) && std::isfinite(maxY) &&
                    std::isfinite(minZ) && std::isfinite(maxZ)) {
                    ForEachSectionInBox(minX, minY, minZ, maxX, maxY, maxZ, ACTIVATION_RADIUS_CHUNKS, requiredActiveSet);
                }
            }
        }
    }

    // Ascertain if any newly activated chunks require emergency data provisioning
    for (int64_t packed : requiredActiveSet) {
        if (m_TerrainSystem->PrioritizeChunk(packed)) {
            result.updateBuildChunks.push_back(packed);
        }
    }

    std::vector<int64_t> currentlyActiveList = m_TerrainSystem->GetActiveChunkPositions();
    std::unordered_set<int64_t> currentlyActive(currentlyActiveList.begin(), currentlyActiveList.end());

    // Deactivate physics for chunks far behind the movement envelope
    for (int64_t packed : currentlyActive) {
        if (requiredActiveSet.find(packed) == requiredActiveSet.end()) {
            m_TerrainSystem->DeactivateChunk(packed);
        }
    }

    // Engage physics for chunks directly within the collision path
    for (int64_t packed : requiredActiveSet) {
        if (currentlyActive.find(packed) == currentlyActive.end()) {
            bool needsData = m_TerrainSystem->ActivateChunk(packed);
            if (needsData) {
                result.updateBuildChunks.push_back(packed);
            }
        }
    }

    return result;
}

/**
 * @brief Inflates a given bounding box by a chunk radius and yields all intersecting chunk section coordinates.
 * 
 * Features a safety brake circuit that calculates the absolute volume of the geometry box before processing.
 * If the computed bounds stretch across a massive swath of the map (e.g. tracking a severely glitched body),
 * the routine will abort gracefully rather than triggering a multi-trillion chunk Out-Of-Memory catastrophe.
 */
void TerrainTracker::ForEachSectionInBox(float minX, float minY, float minZ, float maxX, float maxY, float maxZ, int radiusInChunks, std::unordered_set<int64_t>& outChunks) {
    if (!std::isfinite(minX) || !std::isfinite(maxX) || !std::isfinite(minZ) || !std::isfinite(maxZ)) return;

    int64_t minSectionX = BlockToSectionCoord(minX) - radiusInChunks;
    int64_t minSectionY = BlockToSectionCoord(minY) - radiusInChunks;
    int64_t minSectionZ = BlockToSectionCoord(minZ) - radiusInChunks;
    int64_t maxSectionX = BlockToSectionCoord(maxX) + radiusInChunks;
    int64_t maxSectionY = BlockToSectionCoord(maxY) + radiusInChunks;
    int64_t maxSectionZ = BlockToSectionCoord(maxZ) + radiusInChunks;

    // Constrain chunk allocations within the reasonable generation bounds
    int64_t wMinY = -250 >> 4;
    int64_t wMaxY = 500 >> 4;

    int64_t width = maxSectionX - minSectionX + 1;
    int64_t height = maxSectionY - minSectionY + 1;
    int64_t depth = maxSectionZ - minSectionZ + 1;

    if (width <= 0 || height <= 0 || depth <= 0) return;

    int64_t totalVolume = width * height * depth;

    // Safety Brake Circuit: The most critical defense against infinite velocity glitches
    if (totalVolume > MAX_CHUNKS_PER_CLUSTER_ITERATION) {
        std::cerr << "[Velthoric Native] Terrain Tracker Safety Brake triggered! Ignored request for " 
                  << totalVolume << " chunks in one cluster. (Bounds: " 
                  << minSectionX << "," << minSectionZ << " to " 
                  << maxSectionX << "," << maxSectionZ << ")\n";
        return;
    }

    for (int64_t y = minSectionY; y <= maxSectionY; ++y) {
        if (y < wMinY || y >= wMaxY) continue;
        for (int64_t z = minSectionZ; z <= maxSectionZ; ++z) {
            for (int64_t x = minSectionX; x <= maxSectionX; ++x) {
                outChunks.insert(SectionAsLong(x, y, z));
            }
        }
    }
}

/**
 * @brief Evicts all tracking state and unconditionally releases all active terrain holds.
 * Methodically sweeps the TerrainSystem dependencies and destroys the native physics representation of chunks.
 */
void TerrainTracker::Clear() {
    if (!m_TerrainSystem) return;
    for (int64_t packed : m_PreviouslyRequiredChunks) {
        m_TerrainSystem->ReleaseChunk(packed);
    }
    m_PreviouslyRequiredChunks.clear();
    
    std::vector<int64_t> activeList = m_TerrainSystem->GetActiveChunkPositions();
    for (int64_t packed : activeList) {
        m_TerrainSystem->DeactivateChunk(packed);
    }
}

} // namespace Velthoric

extern "C" {

/**
 * @brief Native JNI bridge to instantiate a Velthoric::TerrainTracker.
 * 
 * Translates the raw virtual memory pointers given by Java into C++ object instances
 * and dynamically allocates the tracker.
 * 
 * @param env The standard JNI environment context.
 * @param clazz The static Java class reference representing TerrainTracker.
 * @param physicsSystemVa A direct pointer to the `JPH::PhysicsSystem` governing the world.
 * @param terrainSystemVa A direct pointer to the `Velthoric::TerrainSystem` orchestrating terrain geometry.
 * @param terrainLayer The dedicated `Jolt ObjectLayer` that marks chunks to be ignored by tracking routines.
 * @return The 64-bit raw pointer pointing to the newly allocated C++ object, returned as `jlong`.
 */
JNIEXPORT jlong JNICALL Java_net_xmx_velthoric_jni_TerrainTracker_nCreate(
    JNIEnv* env, jclass clazz, jlong physicsSystemVa, jlong terrainSystemVa, jshort terrainLayer) {
    (void)env; (void)clazz;
    auto* ps = reinterpret_cast<JPH::PhysicsSystem*>(physicsSystemVa);
    auto* ts = reinterpret_cast<Velthoric::TerrainSystem*>(terrainSystemVa);
    if (!ps || !ts) return 0;
    return reinterpret_cast<jlong>(new Velthoric::TerrainTracker(ps, ts, terrainLayer));
}

/**
 * @brief Native JNI bridge to obliterate a Velthoric::TerrainTracker and recover its memory.
 * 
 * Executes the C++ delete operator on the provided handle, freeing its heap allocation natively.
 * 
 * @param env The standard JNI environment context.
 * @param clazz The static Java class reference.
 * @param handle The 64-bit pointer address acquired from `nCreate`.
 */
JNIEXPORT void JNICALL Java_net_xmx_velthoric_jni_TerrainTracker_nDestroy(
    JNIEnv* env, jclass clazz, jlong handle) {
    (void)env; (void)clazz;
    auto* tracker = reinterpret_cast<Velthoric::TerrainTracker*>(handle);
    delete tracker;
}

/**
 * @brief Native JNI bridge executing the central update loop of the TerrainTracker.
 * 
 * Invokes the highly optimized `Update()` mechanism natively, retrieves the lists of Minecraft chunks
 * requiring initial or update voxel data, and efficiently pushes those coordinates directly into 
 * the shared Java `DirectByteBuffer` memory via zero-copy C++ pointer dereferencing.
 * 
 * @param env The standard JNI environment context.
 * @param clazz The static Java class reference.
 * @param handle The 64-bit pointer address of the TerrainTracker.
 * @param outInitialBuffer DirectByteBuffer referencing memory for new chunk deliveries.
 * @param outUpdateBuffer DirectByteBuffer referencing memory for chunk resubmissions.
 * @return An ingeniously packed 64-bit payload where `(High 32-Bits = Size of Initial Chunks)` 
 *         and `(Low 32-Bits = Size of Update Chunks)`.
 */
JNIEXPORT jlong JNICALL Java_net_xmx_velthoric_jni_TerrainTracker_nUpdate(
    JNIEnv* env, jclass clazz, jlong handle, jobject outInitialBuffer, jobject outUpdateBuffer) {
    (void)clazz;
    auto* tracker = reinterpret_cast<Velthoric::TerrainTracker*>(handle);
    if (!tracker) return 0;

    Velthoric::TerrainTrackerResult res = tracker->Update();

    int initialCount = static_cast<int>(res.initialBuildChunks.size());
    int updateCount = static_cast<int>(res.updateBuildChunks.size());

    // Write directly into shared off-heap Java memory pointer
    if (initialCount > 0 && outInitialBuffer) {
        int64_t* initialData = reinterpret_cast<int64_t*>(env->GetDirectBufferAddress(outInitialBuffer));
        jlong capacity = env->GetDirectBufferCapacity(outInitialBuffer) / sizeof(int64_t);
        int toCopy = std::min(initialCount, static_cast<int>(capacity));
        std::copy_n(res.initialBuildChunks.begin(), toCopy, initialData);
        initialCount = toCopy; // Adjust return count if truncated by buffer size
    }

    if (updateCount > 0 && outUpdateBuffer) {
        int64_t* updateData = reinterpret_cast<int64_t*>(env->GetDirectBufferAddress(outUpdateBuffer));
        jlong capacity = env->GetDirectBufferCapacity(outUpdateBuffer) / sizeof(int64_t);
        int toCopy = std::min(updateCount, static_cast<int>(capacity));
        std::copy_n(res.updateBuildChunks.begin(), toCopy, updateData);
        updateCount = toCopy;
    }

    // Combine both array lengths symmetrically into a single retrievable long
    return (static_cast<jlong>(initialCount) << 32) | static_cast<jlong>(updateCount);
}

/**
 * @brief Native JNI bridge signaling the TerrainTracker to forcibly evict all memory caches.
 * 
 * Bypasses tracking algorithms to directly cascade a release and deactivation mandate across all chunks.
 * 
 * @param env The standard JNI environment context.
 * @param clazz The static Java class reference.
 * @param handle The 64-bit pointer address of the TerrainTracker.
 */
JNIEXPORT void JNICALL Java_net_xmx_velthoric_jni_TerrainTracker_nClear(
    JNIEnv* env, jclass clazz, jlong handle) {
    (void)env; (void)clazz;
    auto* tracker = reinterpret_cast<Velthoric::TerrainTracker*>(handle);
    if (tracker) tracker->Clear();
}

} // extern "C"