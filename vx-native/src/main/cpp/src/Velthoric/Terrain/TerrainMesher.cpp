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

using namespace JPH;

namespace Velthoric {

TerrainShapeCache TerrainMesher::s_ShapeCache(4096);

ShapeRefC TerrainShapeCache::Get(int key) {
    std::lock_guard<std::mutex> lock(m_Mutex);
    auto it = m_CacheMap.find(key);
    if (it == m_CacheMap.end()) return nullptr;
    m_LruList.splice(m_LruList.end(), m_LruList, it->second);
    return it->second->shape;
}

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

void TerrainShapeCache::Clear() {
    std::lock_guard<std::mutex> lock(m_Mutex);
    m_CacheMap.clear();
    m_LruList.clear();
}

ShapeRefC TerrainMesher::GenerateGreedyMesh(const uint8_t* voxels) {
    TriangleList triangles;
    
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
                    bool voxel1 = (x[d] >= 0) ? (voxels[x[0] | (x[2] << 4) | (x[1] << 8)] != 0) : false;
                    bool voxel2 = (x[d] < 15) ? (voxels[(x[0] + q[0]) | ((x[2] + q[2]) << 4) | ((x[1] + q[1]) << 8)] != 0) : false;
                    
                    mask[n++] = (voxel1 != voxel2) ? (voxel1 ? 1 : 2) : 0;
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
                        
                        if (c != 1) {
                            triangles.push_back(Triangle(Float3(v1.GetX(), v1.GetY(), v1.GetZ()), Float3(v4.GetX(), v4.GetY(), v4.GetZ()), Float3(v2.GetX(), v2.GetY(), v2.GetZ())));
                            triangles.push_back(Triangle(Float3(v2.GetX(), v2.GetY(), v2.GetZ()), Float3(v4.GetX(), v4.GetY(), v4.GetZ()), Float3(v3.GetX(), v3.GetY(), v3.GetZ())));
                        } else {
                            triangles.push_back(Triangle(Float3(v1.GetX(), v1.GetY(), v1.GetZ()), Float3(v2.GetX(), v2.GetY(), v2.GetZ()), Float3(v4.GetX(), v4.GetY(), v4.GetZ())));
                            triangles.push_back(Triangle(Float3(v2.GetX(), v2.GetY(), v2.GetZ()), Float3(v3.GetX(), v3.GetY(), v3.GetZ()), Float3(v4.GetX(), v4.GetY(), v4.GetZ())));
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
    MeshShapeSettings settings(triangles);
    settings.SetEmbedded();
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
Java_net_xmx_velthoric_jni_VxTerrainMesher_nGenerateAndCache(JNIEnv *env, jclass clazz, jint contentHash, jobject buffer) {
    // Check cache first to avoid redundant meshing
    JPH::ShapeRefC shape = Velthoric::TerrainMesher::s_ShapeCache.Get(contentHash);
    if (shape != nullptr) return JNI_TRUE;
    
    if (!buffer) return JNI_FALSE;
    const uint8_t* voxels = static_cast<const uint8_t*>(env->GetDirectBufferAddress(buffer));
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
Java_net_xmx_velthoric_jni_VxTerrainMesher_nCreateTerrainBody(JNIEnv *env, jclass clazz, jlong bodyInterfaceVa, jint contentHash, jfloat posX, jfloat posY, jfloat posZ, jshort objectLayer) {
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
Java_net_xmx_velthoric_jni_VxTerrainMesher_nUpdateBodyShape(JNIEnv *env, jclass clazz, jlong bodyInterfaceVa, jint bodyIdVal, jint contentHash) {
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
Java_net_xmx_velthoric_jni_VxTerrainMesher_nClearCache(JNIEnv *env, jclass clazz) {
    Velthoric::TerrainMesher::s_ShapeCache.Clear();
}