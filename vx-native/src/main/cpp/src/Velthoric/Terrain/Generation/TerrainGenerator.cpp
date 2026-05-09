/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 *
 * Author: xI-Mx-Ix
 */
#include "Velthoric/Terrain/Generation/TerrainGenerator.h"
#include <Jolt/Physics/Collision/Shape/StaticCompoundShape.h>
#include <Jolt/Physics/Body/BodyCreationSettings.h>
#include <Jolt/Physics/Body/Body.h>
#include <vector>
#include "Velthoric/Terrain/Shape/TerrainVoxelShape.h"

/**
 * @brief High-performance terrain shape generation and caching subsystem.
 * 
 * This file implements the conversion of block voxel bounds (AABBs) into optimized 
 * JPH::StaticCompoundShape objects for Jolt Physics. It groups individual JPH::BoxShape
 * primitives into a single static compound.
 * 
 * Additionally, it provides an LRU cache (`TerrainShapeCache`) to avoid 
 * duplicating physical shapes for identical chunk configurations, reducing 
 * memory footprint and JNI transition overhead. It also caches BoxShapeSettings
 * for identical block dimensions to speed up compound creation.
 */

using namespace JPH;

namespace Velthoric {

TerrainShapeCache TerrainGenerator::s_ShapeCache(4096);
JPH::RefConst<TerrainMaterial> TerrainGenerator::s_Materials[65536] = {};

const char* TerrainMaterial::sTerrainMaterialName = "TerrainMaterial";

/**
 * @brief Retrieves a cached Shape by its content hash.
 * 
 * @param key The 32-bit hash of the chunk's data.
 * @return A JPH::ShapeRefC pointing to the cached shape, or nullptr if not found.
 */
ShapeRefC TerrainShapeCache::Get(int key) {
    std::lock_guard<std::mutex> lock(m_Mutex);
    auto it = m_CacheMap.find(key);
    if (it == m_CacheMap.end()) return nullptr;
    m_LruList.splice(m_LruList.end(), m_LruList, it->second);
    return it->second->shape;
}

/**
 * @brief Inserts a newly generated Shape into the LRU cache.
 * 
 * If the cache exceeds its maximum capacity, the least recently used 
 * shape is evicted and destroyed.
 * 
 * @param key The 32-bit hash of the chunk's data.
 * @param shape The Jolt Shape reference.
 */
void TerrainShapeCache::Put(int key, ShapeRefC shape) {
    if (shape == nullptr) return;
    std::lock_guard<std::mutex> lock(m_Mutex);
    auto it = m_CacheMap.find(key);
    if (it != m_CacheMap.end()) {
        m_LruList.splice(m_LruList.end(), m_LruList, it->second);
        it->second->shape = shape;
        return;
    }
    if (m_CacheMap.size() >= m_Capacity) {
        int oldKey = m_LruList.front().key;
        m_CacheMap.erase(oldKey);
        m_LruList.pop_front();
    }
    m_LruList.push_back({key, shape});
    m_CacheMap[key] = std::prev(m_LruList.end());
}

/**
 * @brief Clears all cached shapes, freeing native memory.
 */
void TerrainShapeCache::Clear() {
    std::lock_guard<std::mutex> lock(m_Mutex);
    m_CacheMap.clear();
    m_LruList.clear();
}

/**
 * Helper structures for BoxShapeSettings caching.
 */
struct BoxSettingsKey {
    float hx, hy, hz;
    uint32_t matId;
    bool operator==(const BoxSettingsKey& o) const {
        return hx == o.hx && hy == o.hy && hz == o.hz && matId == o.matId;
    }
};

struct BoxSettingsHash {
    std::size_t operator()(const BoxSettingsKey& k) const {
        return std::hash<float>()(k.hx) ^ (std::hash<float>()(k.hy) << 1) ^ (std::hash<float>()(k.hz) << 2) ^ std::hash<uint32_t>()(k.matId);
    }
};

/**
 * @brief Generates an optimized Jolt StaticCompoundShape using BoxShapes.
 * 
 * Iterates through the provided BoxShapeData array, creating BoxShapeSettings for each 
 * unique dimension-material combination, and adding them to a StaticCompoundShapeSettings.
 * 
 * @param boxes An array of BoxShapeData containing local positions, extents, and materials.
 * @param boxCount The number of boxes in the array.
 * @return A valid JPH::ShapeRefC, or nullptr if generation fails or boxCount is 0.
 */
ShapeRefC TerrainGenerator::GenerateCompoundShape(const BoxShapeData* boxes, int boxCount) {
    if (boxCount <= 0) return nullptr;

    TerrainVoxelShapeSettings settings(boxes, boxCount);
    Shape::ShapeResult result = settings.Create();
    
    if (result.IsValid()) {
        return result.Get();
    }
    
    return nullptr;
}

} // namespace Velthoric