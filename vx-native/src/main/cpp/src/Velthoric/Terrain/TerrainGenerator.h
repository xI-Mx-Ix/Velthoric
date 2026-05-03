/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 *
 * Author: xI-Mx-Ix
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
#include <Jolt/Physics/Collision/Shape/BoxShape.h>

namespace Velthoric {

/**
 * Custom Jolt Physics Material to store native friction and restitution for terrain.
 */
class TerrainMaterial : public JPH::PhysicsMaterial {
public:
    uint32_t mMaterialId;
    float mFriction;
    float mRestitution;

    TerrainMaterial(uint32_t matId, float friction, float restitution) 
        : mMaterialId(matId), mFriction(friction), mRestitution(restitution) {}

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
 * Data structure representing a single box shape passed from Java.
 * It contains the local center position, the half extents, and the material ID.
 */
#pragma pack(push, 1)
struct BoxShapeData {
    float cx, cy, cz;
    float hx, hy, hz;
    uint32_t matId;
};
#pragma pack(pop)

/**
 * High-performance terrain shape generation subsystem.
 * 
 * Responsible for generating StaticCompoundShapes containing BoxShapes from
 * provided voxel bounds. This system operates entirely in C++ to avoid Java
 * object allocation and JNI marshaling costs during chunk shape generation.
 */
class TerrainGenerator {
public:
    // Global static cache instance for the final chunk shapes
    static TerrainShapeCache s_ShapeCache;
    
    // Global static materials registry (0 is unused, 1 is default)
    static JPH::RefConst<TerrainMaterial> s_Materials[65536];

    /**
     * Generates a Jolt StaticCompoundShape from an array of BoxShapeData.
     * 
     * Uses a local cache for BoxShapeSettings to avoid recreating identical boxes.
     * 
     * @param boxes Pointer to an array of BoxShapeData.
     * @param boxCount Number of boxes in the array.
     * @return A RefC pointer to the generated StaticCompoundShape, or nullptr if empty or invalid.
     */
    static JPH::ShapeRefC GenerateCompoundShape(const BoxShapeData* boxes, int boxCount);
};

} // namespace Velthoric