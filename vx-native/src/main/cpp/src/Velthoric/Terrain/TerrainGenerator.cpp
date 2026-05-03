/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 *
 * Author: xI-Mx-Ix
 */
#include "TerrainGenerator.h"
#include <Jolt/Physics/Collision/Shape/StaticCompoundShape.h>
#include <Jolt/Physics/Body/BodyCreationSettings.h>
#include <Jolt/Physics/Body/Body.h>
#include <vector>
#include <jni.h>

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

    StaticCompoundShapeSettings compoundSettings;
    
    // Ensure default material (1) exists
    if (!s_Materials[1]) {
        s_Materials[1] = new TerrainMaterial(1, 0.75f, 0.0f);
    }

    // Cache to reuse BoxShapeSettings for identical dimensions and materials
    std::unordered_map<BoxSettingsKey, Ref<BoxShapeSettings>, BoxSettingsHash> boxSettingsCache;
    
    for (int i = 0; i < boxCount; ++i) {
        const BoxShapeData& d = boxes[i];
        
        BoxSettingsKey key = { d.hx, d.hy, d.hz, d.matId };
        Ref<BoxShapeSettings> settings;
        
        auto it = boxSettingsCache.find(key);
        if (it != boxSettingsCache.end()) {
            settings = it->second;
        } else {
            JPH::RefConst<TerrainMaterial> mat = s_Materials[d.matId];
            if (!mat) mat = s_Materials[1];
            
            settings = new BoxShapeSettings(Vec3(d.hx, d.hy, d.hz), 0.0f, mat);
            boxSettingsCache[key] = settings;
        }

        compoundSettings.AddShape(Vec3(d.cx, d.cy, d.cz), Quat::sIdentity(), settings);
    }
    
    Shape::ShapeResult result = compoundSettings.Create();
    
    if (result.IsValid()) {
        return result.Get();
    }
    
    return nullptr;
}

}

/**
 * Generates and caches a Jolt StaticCompoundShape for the given terrain chunk voxel data.
 * 
 * If a shape with the same content hash already exists in the native cache,
 * the generation is skipped and the cached shape is reused.
 *
 * @param env Pointer to the JNI environment.
 * @param clazz Reference to the Java class.
 * @param contentHash Unique hash representing the chunk's content.
 * @param buffer Java DirectByteBuffer containing the BoxShapeData array.
 * @param boxCount The number of boxes in the buffer.
 * @return True if solid collision geometry was generated/found, false if empty or error.
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_net_xmx_velthoric_jni_TerrainGenerator_nGenerateAndCache(JNIEnv *env, jclass clazz, jint contentHash, jobject buffer, jint boxCount) {
    // Check cache first to avoid redundant generation
    JPH::ShapeRefC shape = Velthoric::TerrainGenerator::s_ShapeCache.Get(contentHash);
    if (shape != nullptr) return JNI_TRUE;
    
    if (boxCount <= 0 || !buffer) return JNI_FALSE;
    const Velthoric::BoxShapeData* boxes = static_cast<const Velthoric::BoxShapeData*>(env->GetDirectBufferAddress(buffer));
    if (!boxes) return JNI_FALSE;
    
    // Generate the compound shape natively
    shape = Velthoric::TerrainGenerator::GenerateCompoundShape(boxes, boxCount);
    if (shape != nullptr) {
        Velthoric::TerrainGenerator::s_ShapeCache.Put(contentHash, shape);
        return JNI_TRUE;
    }
    return JNI_FALSE;
}

/**
 * Creates a new Jolt Physics Body for the terrain chunk using a cached shape.
 *
 * @param env Pointer to the JNI environment.
 * @param clazz Reference to the Java class.
 * @param bodyInterfaceVa Native pointer to the Jolt BodyInterface.
 * @param contentHash Hash of the shape to look up in the cache.
 * @param posX World X coordinate of the chunk.
 * @param posY World Y coordinate of the chunk.
 * @param posZ World Z coordinate of the chunk.
 * @param objectLayer The Jolt object layer assigned to terrain bodies.
 * @return The internal Jolt BodyID, or cInvalidBodyID if creation fails.
 */
extern "C" JNIEXPORT jint JNICALL
Java_net_xmx_velthoric_jni_TerrainGenerator_nCreateTerrainBody(JNIEnv *env, jclass clazz, jlong bodyInterfaceVa, jint contentHash, jfloat posX, jfloat posY, jfloat posZ, jshort objectLayer) {
    JPH::BodyInterface* bi = reinterpret_cast<JPH::BodyInterface*>(bodyInterfaceVa);
    if (!bi) return JPH::BodyID::cInvalidBodyID;

    JPH::ShapeRefC shape = Velthoric::TerrainGenerator::s_ShapeCache.Get(contentHash);
    if (shape == nullptr) return JPH::BodyID::cInvalidBodyID;

    JPH::BodyCreationSettings bcs(shape, JPH::RVec3(posX, posY, posZ), JPH::Quat::sIdentity(), JPH::EMotionType::Static, objectLayer);
    bcs.mFriction = 0.75f;
    
    JPH::Body* body = bi->CreateBody(bcs);
    if (body) {
        return body->GetID().GetIndexAndSequenceNumber();
    }
    return JPH::BodyID::cInvalidBodyID;
}

/**
 * Updates an existing Jolt Body with a new cached shape.
 *
 * @param env Pointer to the JNI environment.
 * @param clazz Reference to the Java class.
 * @param bodyInterfaceVa Native pointer to the Jolt BodyInterface.
 * @param bodyIdVal The Jolt BodyID of the chunk's body.
 * @param contentHash Hash of the new shape to look up in the cache.
 */
extern "C" JNIEXPORT void JNICALL
Java_net_xmx_velthoric_jni_TerrainGenerator_nUpdateBodyShape(JNIEnv *env, jclass clazz, jlong bodyInterfaceVa, jint bodyIdVal, jint contentHash) {
    JPH::BodyInterface* bi = reinterpret_cast<JPH::BodyInterface*>(bodyInterfaceVa);
    if (!bi) return;

    JPH::ShapeRefC shape = Velthoric::TerrainGenerator::s_ShapeCache.Get(contentHash);
    if (shape == nullptr) return;

    JPH::BodyID bodyId(bodyIdVal);
    bi->SetShape(bodyId, shape, true, JPH::EActivation::DontActivate);
}

/**
 * Clears the native terrain shape cache.
 * 
 * Called during shutdown to free all unreferenced shape resources.
 *
 * @param env Pointer to the JNI environment.
 * @param clazz Reference to the Java class.
 */
extern "C" JNIEXPORT void JNICALL
Java_net_xmx_velthoric_jni_TerrainGenerator_nClearCache(JNIEnv *env, jclass clazz) {
    Velthoric::TerrainGenerator::s_ShapeCache.Clear();
}

/**
 * Registers a terrain material with specific friction and restitution.
 *
 * @param env Pointer to the JNI environment.
 * @param clazz Reference to the Java class.
 * @param id Material ID (1-65535).
 * @param friction Material friction.
 * @param restitution Material restitution.
 */
extern "C" JNIEXPORT void JNICALL
Java_net_xmx_velthoric_jni_TerrainGenerator_nRegisterMaterial(JNIEnv *env, jclass clazz, jint id, jfloat friction, jfloat restitution) {
    if (id > 0 && id < 65536) {
        Velthoric::TerrainGenerator::s_Materials[id] = new Velthoric::TerrainMaterial(id, friction, restitution);
    }
}