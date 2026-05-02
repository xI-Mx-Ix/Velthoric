/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 *
 * Author: xI-Mx-Ix
 */
#include "TerrainMesher.h"
#include <Jolt/Geometry/Triangle.h>
#include <Jolt/Physics/Collision/Shape/MeshShape.h>
#include <Jolt/Physics/Body/BodyCreationSettings.h>
#include <Jolt/Physics/Body/Body.h>
#include <vector>
#include <jni.h>

/**
 * @brief High-performance terrain meshing and caching subsystem.
 * 
 * This file implements a greedy meshing algorithm to convert 16x16x16 voxel 
 * chunk data into optimized JPH::MeshShape objects for Jolt Physics.
 * The greedy meshing reduces the polygon count significantly by merging adjacent 
 * blocks with the same material into larger rectangular quads. 
 * 
 * Additionally, it provides an LRU cache (`TerrainShapeCache`) to avoid 
 * duplicating physical shapes for identical chunk configurations, reducing 
 * memory footprint and JNI transition overhead.
 */

using namespace JPH;

namespace Velthoric {

TerrainShapeCache TerrainMesher::s_ShapeCache(4096);
JPH::RefConst<TerrainMaterial> TerrainMesher::s_Materials[65536] = {};

const char* TerrainMaterial::sTerrainMaterialName = "TerrainMaterial";

/**
 * @brief Retrieves a cached MeshShape by its content hash.
 * 
 * @param key The 32-bit hash of the chunk's voxel data.
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
 * @brief Inserts a newly generated MeshShape into the LRU cache.
 * 
 * If the cache exceeds its maximum capacity, the least recently used 
 * shape is evicted and destroyed.
 * 
 * @param key The 32-bit hash of the chunk's voxel data.
 * @param shape The Jolt MeshShape reference.
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
 * @brief Generates an optimized Jolt MeshShape using greedy meshing.
 * 
 * The algorithm iterates over the 16x16x16 voxel grid across 3 dimensions (X, Y, Z).
 * It calculates visible block faces and merges adjacent faces of the same material 
 * into large rectangles. This reduces physics geometry complexity by up to 90%, 
 * significantly speeding up collision detection.
 * 
 * @param voxels A flat array of 4096 16-bit block material IDs.
 * @return A valid JPH::ShapeRefC, or nullptr if the chunk is completely empty (air).
 */
ShapeRefC TerrainMesher::GenerateGreedyMesh(const uint16_t* voxels) {
    TriangleList triangles;
    PhysicsMaterialList physicsMaterials;
    std::unordered_map<int, uint32_t> matIdToIndex;

    // Ensure default material (1) exists
    if (!s_Materials[1]) {
        s_Materials[1] = new TerrainMaterial(0.75f, 0.0f);
    }
    
    // Pass 1: Greedy meshing algorithm over a 16x16x16 voxel grid.
    for (int d = 0; d < 3; d++) {
        int u = (d + 1) % 3;
        int v = (d + 2) % 3;
        
        int x[3] = {0};
        int q[3] = {0};
        q[d] = 1;
        
        int mask[16 * 16];
        
        for (x[d] = -1; x[d] < 16; ) {
            int n = 0;
            for (x[v] = 0; x[v] < 16; ++x[v]) {
                for (x[u] = 0; x[u] < 16; ++x[u]) {
                    // Indexing: x | (z << 4) | (y << 8)
                    uint16_t m1 = (x[d] >= 0) ? voxels[x[0] | (x[2] << 4) | (x[1] << 8)] : 0;
                    uint16_t m2 = (x[d] < 15) ? voxels[(x[0] + q[0]) | ((x[2] + q[2]) << 4) | ((x[1] + q[1]) << 8)] : 0;
                    
                    int m = 0;
                    if (m1 != 0 && m2 == 0) m = (m1 << 8) | 1;
                    else if (m1 == 0 && m2 != 0) m = (m2 << 8) | 2;
                    
                    mask[n++] = m;
                }
            }
            
            ++x[d];
            n = 0;
            
            for (int j = 0; j < 16; ++j) {
                for (int i = 0; i < 16; ) {
                    int c = mask[n];
                    if (c != 0) {
                        int w, h;
                        for (w = 1; i + w < 16 && mask[n + w] == c; ++w) {}
                        
                        bool done = false;
                        for (h = 1; j + h < 16; ++h) {
                            for (int k = 0; k < w; ++k) {
                                if (mask[n + k + h * 16] != c) {
                                    done = true;
                                    break;
                                }
                            }
                            if (done) break;
                        }
                        
                        x[u] = i;
                        x[v] = j;
                        
                        int du[3] = {0}; du[u] = w;
                        int dv[3] = {0}; dv[v] = h;
                        
                        Vec3 v1(x[0], x[1], x[2]);
                        Vec3 v2(x[0] + du[0], x[1] + du[1], x[2] + du[2]);
                        Vec3 v3(x[0] + du[0] + dv[0], x[1] + du[1] + dv[1], x[2] + du[2] + dv[2]);
                        Vec3 v4(x[0] + dv[0], x[1] + dv[1], x[2] + dv[2]);
                        
                        int matId = c >> 8;
                        int dir = c & 0xFF;

                        uint32_t matIndex = 0;
                        if (matIdToIndex.find(matId) == matIdToIndex.end()) {
                            matIndex = (uint32_t)physicsMaterials.size();
                            matIdToIndex[matId] = matIndex;
                            JPH::RefConst<TerrainMaterial> mat = s_Materials[matId];
                            if (!mat) mat = s_Materials[1];
                            physicsMaterials.push_back(mat.GetPtr());
                        } else {
                            matIndex = matIdToIndex[matId];
                        }

                        if (dir != 1) {
                            triangles.push_back(Triangle(Float3(v1.GetX(), v1.GetY(), v1.GetZ()), Float3(v4.GetX(), v4.GetY(), v4.GetZ()), Float3(v2.GetX(), v2.GetY(), v2.GetZ()), matIndex));
                            triangles.push_back(Triangle(Float3(v2.GetX(), v2.GetY(), v2.GetZ()), Float3(v4.GetX(), v4.GetY(), v4.GetZ()), Float3(v3.GetX(), v3.GetY(), v3.GetZ()), matIndex));
                        } else {
                            triangles.push_back(Triangle(Float3(v1.GetX(), v1.GetY(), v1.GetZ()), Float3(v2.GetX(), v2.GetY(), v2.GetZ()), Float3(v4.GetX(), v4.GetY(), v4.GetZ()), matIndex));
                            triangles.push_back(Triangle(Float3(v2.GetX(), v2.GetY(), v2.GetZ()), Float3(v3.GetX(), v3.GetY(), v3.GetZ()), Float3(v4.GetX(), v4.GetY(), v4.GetZ()), matIndex));
                        }
                        
                        for (int l = 0; l < h; ++l) {
                            for (int k = 0; k < w; ++k) {
                                mask[n + k + l * 16] = 0;
                            }
                        }
                        
                        i += w;
                        n += w;
                    } else {
                        i++;
                        n++;
                    }
                }
            }
        }
    }
    
    if (triangles.empty()) return nullptr;
    
    // Pass 2: Mesh compilation and embedding.
    MeshShapeSettings settings(triangles, physicsMaterials);
    settings.SetEmbedded();
    
    // Enable active edge detection to prevent sticking at block transitions.
    // This is crucial for smooth sliding over greedy-meshed terrain.
    settings.mActiveEdgeCosThresholdAngle = 0.996f; // approx 5 degrees
    
    Shape::ShapeResult result = settings.Create();
    
    if (result.IsValid()) {
        return result.Get();
    }
    
    return nullptr;
}

}

/**
 * Generates and caches a Jolt MeshShape for the given terrain chunk voxel data.
 * 
 * If a shape with the same content hash already exists in the native cache,
 * the generation is skipped and the cached shape is reused.
 *
 * @param env Pointer to the JNI environment.
 * @param clazz Reference to the Java class.
 * @param contentHash Unique hash representing the chunk's content.
 * @param buffer Java DirectByteBuffer containing the 16x16x16 voxel grid.
 * @return True if solid collision geometry was generated/found, false if air.
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_net_xmx_velthoric_jni_TerrainMesher_nGenerateAndCache(JNIEnv *env, jclass clazz, jint contentHash, jobject buffer) {
    // Check cache first to avoid redundant meshing
    JPH::ShapeRefC shape = Velthoric::TerrainMesher::s_ShapeCache.Get(contentHash);
    if (shape != nullptr) return JNI_TRUE;
    
    if (!buffer) return JNI_FALSE;
    const uint16_t* voxels = static_cast<const uint16_t*>(env->GetDirectBufferAddress(buffer));
    if (!voxels) return JNI_FALSE;
    
    // Generate the optimized MeshShape natively
    shape = Velthoric::TerrainMesher::GenerateGreedyMesh(voxels);
    if (shape != nullptr) {
        Velthoric::TerrainMesher::s_ShapeCache.Put(contentHash, shape);
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
Java_net_xmx_velthoric_jni_TerrainMesher_nCreateTerrainBody(JNIEnv *env, jclass clazz, jlong bodyInterfaceVa, jint contentHash, jfloat posX, jfloat posY, jfloat posZ, jshort objectLayer) {
    JPH::BodyInterface* bi = reinterpret_cast<JPH::BodyInterface*>(bodyInterfaceVa);
    if (!bi) return JPH::BodyID::cInvalidBodyID;

    JPH::ShapeRefC shape = Velthoric::TerrainMesher::s_ShapeCache.Get(contentHash);
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
Java_net_xmx_velthoric_jni_TerrainMesher_nUpdateBodyShape(JNIEnv *env, jclass clazz, jlong bodyInterfaceVa, jint bodyIdVal, jint contentHash) {
    JPH::BodyInterface* bi = reinterpret_cast<JPH::BodyInterface*>(bodyInterfaceVa);
    if (!bi) return;

    JPH::ShapeRefC shape = Velthoric::TerrainMesher::s_ShapeCache.Get(contentHash);
    if (shape == nullptr) return;

    JPH::BodyID bodyId(bodyIdVal);
    bi->SetShape(bodyId, shape, true, JPH::EActivation::DontActivate);
}

/**
 * Clears the native terrain shape cache.
 * 
 * Called during shutdown to free all unreferenced mesh resources.
 *
 * @param env Pointer to the JNI environment.
 * @param clazz Reference to the Java class.
 */
extern "C" JNIEXPORT void JNICALL
Java_net_xmx_velthoric_jni_TerrainMesher_nClearCache(JNIEnv *env, jclass clazz) {
    Velthoric::TerrainMesher::s_ShapeCache.Clear();
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
Java_net_xmx_velthoric_jni_TerrainMesher_nRegisterMaterial(JNIEnv *env, jclass clazz, jint id, jfloat friction, jfloat restitution) {
    if (id > 0 && id < 65536) {
        Velthoric::TerrainMesher::s_Materials[id] = new Velthoric::TerrainMaterial(friction, restitution);
    }
}