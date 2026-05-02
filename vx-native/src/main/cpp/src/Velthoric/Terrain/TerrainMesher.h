/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 *
 * Author xI-Mx-Ix
 */
#pragma once
#include <Jolt/Jolt.h>
#include <Jolt/Physics/Collision/Shape/Shape.h>
#include <Jolt/Physics/Body/BodyInterface.h>
#include <cstdint>
#include <unordered_map>
#include <list>
#include <mutex>
#include <Jolt/Physics/Collision/PhysicsMaterial.h>

namespace Velthoric {

/**
 * Custom Jolt Physics Material to store native friction and restitution for terrain.
 */
class TerrainMaterial : public JPH::PhysicsMaterial {
public:
    float mFriction;
    float mRestitution;

    TerrainMaterial(float friction, float restitution) : mFriction(friction), mRestitution(restitution) {}

    static const char* sTerrainMaterialName;
    virtual const char* GetDebugName() const override { return sTerrainMaterialName; }
    virtual JPH::Color GetDebugColor() const override { return JPH::Color::sGreen; }
};

/**
 * An LRU (Least Recently Used) cache for natively generated Jolt Shapes.
 * 
 * Caching and retrieving shapes natively eliminates JNI overhead and prevents
 * the need to handle native pointers or memory management on the Java side.
 * The cache is completely thread-safe and auto-evicts the oldest shapes when
 * capacity is exceeded.
 */
class TerrainShapeCache {
    struct CacheEntry {
        int key;
        JPH::ShapeRefC shape;
    };
    
    std::list<CacheEntry> m_LruList;
    std::unordered_map<int, decltype(m_LruList)::iterator> m_CacheMap;
    int m_Capacity;
    std::mutex m_Mutex;

public:
    /**
     * Initializes the terrain shape cache.
     *
     * @param capacity Maximum number of shapes to retain in memory.
     */
    TerrainShapeCache(int capacity) : m_Capacity(capacity) {}

    /**
     * Retrieves a shape from the cache and marks it as recently used.
     *
     * @param key The content hash key.
     * @return The cached shape, or nullptr if not found.
     */
    JPH::ShapeRefC Get(int key);

    /**
     * Adds a shape to the cache. Evicts the oldest if capacity is exceeded.
     *
     * @param key The content hash key.
     * @param shape The shape to cache.
     */
    void Put(int key, JPH::ShapeRefC shape);

    /**
     * Clears all shapes from the cache, releasing their native memory.
     */
    void Clear();
};

/**
 * High-performance terrain meshing subsystem.
 * 
 * Responsible for generating greedy meshes from voxel data buffers. This system
 * operates entirely in C++ to avoid Java object allocation and JNI marshaling 
 * costs during chunk shape generation.
 */
class TerrainMesher {
public:
    // Global static cache instance
    static TerrainShapeCache s_ShapeCache;
    
    // Global static materials registry (0 is unused, 1 is default)
    static JPH::RefConst<TerrainMaterial> s_Materials[65536];

    /**
     * Generates a Jolt MeshShape from a 16x16x16 voxel grid.
     * 
     * The algorithm merges adjacent coplanar faces to drastically reduce the 
     * triangle count compared to a naive voxel meshing approach.
     * 
     * @param voxels Pointer to a 8192-byte array representing a 16x16x16 chunk section of 16-bit material IDs.
     *               A value of 0 means empty (air), any non-zero value means solid.
     *               Indexing format: x | (z << 4) | (y << 8)
     * @return A RefC pointer to the generated MeshShape, or nullptr if the chunk is empty.
     */
    static JPH::ShapeRefC GenerateGreedyMesh(const uint16_t* voxels);
};

} // namespace Velthoric