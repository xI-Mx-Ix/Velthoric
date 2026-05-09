/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 *
 * Author: xI-Mx-Ix
 */
#include "Velthoric/Terrain/TerrainSystem.h"
#include <Jolt/Physics/Body/BodyCreationSettings.h>
#include <Jolt/Physics/Body/Body.h>
#include <Jolt/Physics/Collision/Shape/StaticCompoundShape.h>
#include <jni.h>
#include "Velthoric/Terrain/Shape/TerrainVoxelShape.h"

namespace Velthoric {

/**
 * @brief Constructs the terrain system.
 *
 * Stores references to the Jolt body interface and terrain object layer
 * for use in all subsequent body creation and management operations.
 *
 * @param bodyInterface Pointer to the locking Jolt BodyInterface.
 * @param terrainLayer The Jolt object layer for terrain bodies.
 */
TerrainSystem::TerrainSystem(JPH::BodyInterface* bodyInterface, uint16_t terrainLayer)
    : m_BodyInterface(bodyInterface), m_TerrainLayer(terrainLayer) {
}

/**
 * @brief Destroys the terrain system and ensures all bodies are cleaned up.
 */
TerrainSystem::~TerrainSystem() {
    CleanupAllBodies();
}

/**
 * @brief Requests a terrain chunk, incrementing its reference count.
 *
 * @param packedPos The bit-packed section coordinate.
 * @return True if this is a new chunk that needs voxel data from Java.
 */
bool TerrainSystem::RequestChunk(int64_t packedPos) {
    return m_StateManager.RequestChunk(packedPos);
}

/**
 * @brief Releases a terrain chunk, decrementing its reference count.
 *
 * When the count reaches zero, the body is destroyed and the entry is removed.
 *
 * @param packedPos The bit-packed section coordinate.
 */
void TerrainSystem::ReleaseChunk(int64_t packedPos) {
    if (m_StateManager.ReleaseChunk(packedPos)) {
        m_StateManager.SetState(packedPos, TerrainStateManager::STATE_REMOVING);
        RemoveBodyAndShape(packedPos);
        m_StateManager.RemoveChunk(packedPos);
    }
}

/**
 * @brief Activates a chunk by adding its body to the physics simulation.
 *
 * No-op for air chunks or chunks without a body.
 *
 * @param packedPos The bit-packed section coordinate.
 * @return True if the chunk is a placeholder and should be re-submitted.
 */
bool TerrainSystem::ActivateChunk(int64_t packedPos) {
    int state = m_StateManager.GetState(packedPos);
    if (state == TerrainStateManager::STATE_AIR_CHUNK) return false;

    uint32_t bodyId = m_StateManager.GetBodyId(packedPos);

    if (bodyId != TerrainStateManager::UNUSED_BODY_ID && state == TerrainStateManager::STATE_READY_INACTIVE) {
        m_StateManager.SetState(packedPos, TerrainStateManager::STATE_READY_ACTIVE);
        if (m_BodyInterface && !m_BodyInterface->IsAdded(JPH::BodyID(bodyId))) {
            m_BodyInterface->AddBody(JPH::BodyID(bodyId), JPH::EActivation::Activate);
        }
    }

    return m_StateManager.IsPlaceholder(packedPos) && bodyId != TerrainStateManager::UNUSED_BODY_ID;
}

/**
 * @brief Deactivates a chunk by removing its body from the simulation.
 *
 * The body is retained in Jolt and can be re-activated later without recreation.
 *
 * @param packedPos The bit-packed section coordinate.
 */
void TerrainSystem::DeactivateChunk(int64_t packedPos) {
    int state = m_StateManager.GetState(packedPos);
    uint32_t bodyId = m_StateManager.GetBodyId(packedPos);

    if (bodyId != TerrainStateManager::UNUSED_BODY_ID && state == TerrainStateManager::STATE_READY_ACTIVE) {
        m_StateManager.SetState(packedPos, TerrainStateManager::STATE_READY_INACTIVE);
        if (m_BodyInterface && m_BodyInterface->IsAdded(JPH::BodyID(bodyId))) {
            m_BodyInterface->RemoveBody(JPH::BodyID(bodyId));
        }
    }
}

/**
 * @brief Checks if a chunk should be prioritized for data submission.
 *
 * @param packedPos The bit-packed section coordinate.
 * @return True if the chunk needs voxel data.
 */
bool TerrainSystem::PrioritizeChunk(int64_t packedPos) {
    if (!m_StateManager.IsManaged(packedPos)) return false;
    return m_StateManager.IsPlaceholder(packedPos) || !m_StateManager.IsReady(packedPos);
}

/**
 * @brief Receives voxel data from Java and creates or updates the chunk body.
 *
 * This is the main entry point for chunk data. It generates a Jolt shape via
 * TerrainGenerator, then either creates a new body or updates the existing one.
 * Empty chunks (boxCount == 0) are marked as air.
 *
 * @param packedPos The bit-packed section coordinate.
 * @param posX World X position for body placement.
 * @param posY World Y position for body placement.
 * @param posZ World Z position for body placement.
 * @param boxes Pointer to the BoxShapeData array.
 * @param boxCount Number of boxes in the array.
 * @param contentHash Unique hash of the chunk content for shape caching.
 * @param isInitialBuild True if this is the first build.
 * @return True if the chunk contains solid geometry.
 */
bool TerrainSystem::SubmitChunkData(int64_t packedPos, float posX, float posY, float posZ,
                                     const BoxShapeData* boxes, int boxCount, int contentHash,
                                     bool isInitialBuild) {
    if (!m_StateManager.IsManaged(packedPos)) return false;
    if (!m_BodyInterface) return false;

    int currentState = m_StateManager.GetState(packedPos);
    if (currentState == TerrainStateManager::STATE_REMOVING) return false;

    bool wasActive = (currentState == TerrainStateManager::STATE_READY_ACTIVE);

    if (boxCount <= 0 || !boxes) {
        uint32_t existingBody = m_StateManager.GetBodyId(packedPos);
        if (existingBody != TerrainStateManager::UNUSED_BODY_ID) {
            RemoveBodyAndShape(packedPos);
        }
        m_StateManager.SetState(packedPos, TerrainStateManager::STATE_AIR_CHUNK);
        m_StateManager.SetPlaceholder(packedPos, isInitialBuild);
        return false;
    }

    JPH::ShapeRefC shape = TerrainGenerator::s_ShapeCache.Get(contentHash);
    if (!shape) {
        shape = TerrainGenerator::GenerateCompoundShape(boxes, boxCount);
        if (shape) {
            TerrainGenerator::s_ShapeCache.Put(contentHash, shape);
        }
    }

    if (!shape) {
        m_StateManager.SetState(packedPos, TerrainStateManager::STATE_AIR_CHUNK);
        m_StateManager.SetPlaceholder(packedPos, isInitialBuild);
        return false;
    }

    uint32_t existingBody = m_StateManager.GetBodyId(packedPos);

    if (existingBody != TerrainStateManager::UNUSED_BODY_ID) {
        m_BodyInterface->SetShape(JPH::BodyID(existingBody), shape, true, JPH::EActivation::DontActivate);
        m_StateManager.SetState(packedPos, wasActive ? TerrainStateManager::STATE_READY_ACTIVE : TerrainStateManager::STATE_READY_INACTIVE);
    } else {
        JPH::BodyCreationSettings bcs(shape, JPH::RVec3(posX, posY, posZ),
                                       JPH::Quat::sIdentity(), JPH::EMotionType::Static, m_TerrainLayer);
        bcs.mFriction = 0.75f;

        JPH::Body* body = m_BodyInterface->CreateBody(bcs);
        if (body) {
            uint32_t newBodyId = body->GetID().GetIndexAndSequenceNumber();
            m_StateManager.SetBodyId(packedPos, newBodyId);
            m_StateManager.SetState(packedPos, TerrainStateManager::STATE_READY_INACTIVE);

            if (wasActive) {
                m_BodyInterface->AddBody(body->GetID(), JPH::EActivation::Activate);
                m_StateManager.SetState(packedPos, TerrainStateManager::STATE_READY_ACTIVE);
            }
        } else {
            m_StateManager.SetState(packedPos, TerrainStateManager::STATE_PENDING_DATA);
            return false;
        }
    }

    m_StateManager.SetPlaceholder(packedPos, isInitialBuild);
    return true;
}

/**
 * @brief Checks if a chunk is ready for physics.
 *
 * @param packedPos The bit-packed section coordinate.
 * @return True if ready.
 */
bool TerrainSystem::IsReady(int64_t packedPos) const {
    return m_StateManager.IsReady(packedPos);
}

/**
 * @brief Checks if a chunk is using a placeholder shape.
 *
 * @param packedPos The bit-packed section coordinate.
 * @return True if placeholder.
 */
bool TerrainSystem::IsPlaceholder(int64_t packedPos) const {
    return m_StateManager.IsPlaceholder(packedPos);
}

/**
 * @brief Checks if a chunk is managed.
 *
 * @param packedPos The bit-packed section coordinate.
 * @return True if managed.
 */
bool TerrainSystem::IsManaged(int64_t packedPos) const {
    return m_StateManager.IsManaged(packedPos);
}

/**
 * @brief Checks if a body ID belongs to a terrain chunk.
 *
 * @param bodyId The Jolt body ID.
 * @return True if terrain body.
 */
bool TerrainSystem::IsTerrainBody(uint32_t bodyId) const {
    return m_StateManager.IsTerrainBody(bodyId);
}

/**
 * @brief Returns packed positions of all active chunks.
 *
 * @return Vector of packed positions.
 */
std::vector<int64_t> TerrainSystem::GetActiveChunkPositions() const {
    return m_StateManager.GetActiveChunkPositions();
}

/**
 * @brief Handles a block update event natively.
 *
 * This method is called from Java when a block in the world changes.
 * it calculates the affected area and wakes up all physics bodies
 * within a 5x5x5 box around the block to ensure correct physics interactions.
 *
 * @param x World X coordinate of the block.
 * @param y World Y coordinate of the block.
 * @param z World Z coordinate of the block.
 */
void TerrainSystem::OnBlockUpdate(int x, int y, int z) {
    if (!m_BodyInterface) return;

    JPH::Vec3 minPos(static_cast<float>(x) - 2.0f, static_cast<float>(y) - 2.0f, static_cast<float>(z) - 2.0f);
    JPH::Vec3 maxPos(static_cast<float>(x) + 3.0f, static_cast<float>(y) + 3.0f, static_cast<float>(z) + 3.0f);
    JPH::AABox box(minPos, maxPos);

    m_BodyInterface->ActivateBodiesInAABox(box, JPH::BroadPhaseLayerFilter(), JPH::ObjectLayerFilter());
}

/**
 * @brief Removes and destroys the physics body for a chunk.
 *
 * @param packedPos The bit-packed section coordinate.
 */
void TerrainSystem::RemoveBodyAndShape(int64_t packedPos) {
    uint32_t bodyId = m_StateManager.GetBodyId(packedPos);
    if (bodyId != TerrainStateManager::UNUSED_BODY_ID && m_BodyInterface) {
        JPH::BodyID id(bodyId);
        if (m_BodyInterface->IsAdded(id)) {
            m_BodyInterface->RemoveBody(id);
        }
        m_BodyInterface->DestroyBody(id);
        m_StateManager.SetBodyId(packedPos, TerrainStateManager::UNUSED_BODY_ID);
    }
}

/**
 * @brief Destroys all physics bodies managed by this system.
 */
void TerrainSystem::CleanupAllBodies() {
    if (!m_BodyInterface) return;

    std::vector<int64_t> positions;
    std::vector<uint32_t> bodyIds;
    m_StateManager.GetAllManagedBodies(positions, bodyIds);

    for (size_t i = 0; i < positions.size(); ++i) {
        if (bodyIds[i] != TerrainStateManager::UNUSED_BODY_ID) {
            JPH::BodyID id(bodyIds[i]);
            if (m_BodyInterface->IsAdded(id)) {
                m_BodyInterface->RemoveBody(id);
            }
            m_BodyInterface->DestroyBody(id);
        }
    }
    m_StateManager.Clear();
}

} // namespace Velthoric

extern "C" {

/**
 * @brief Creates a new native TerrainSystem instance.
 *
 * @param env JNI environment pointer.
 * @param clazz Java class reference.
 * @param bodyInterfaceVa Native address of the locking Jolt BodyInterface.
 * @param terrainLayer The object layer assigned to terrain bodies.
 * @return Native address of the new TerrainSystem, or 0 on failure.
 */
JNIEXPORT jlong JNICALL
Java_net_xmx_velthoric_jni_TerrainSystem_nCreate(JNIEnv* env, jclass clazz, jlong bodyInterfaceVa, jshort terrainLayer) {
    (void)env; (void)clazz;
    JPH::BodyInterface* bi = reinterpret_cast<JPH::BodyInterface*>(bodyInterfaceVa);
    if (!bi) return 0;

    auto* system = new Velthoric::TerrainSystem(bi, static_cast<uint16_t>(terrainLayer));
    return reinterpret_cast<jlong>(system);
}

/**
 * @brief Destroys a native TerrainSystem instance and frees its memory.
 *
 * @param env JNI environment pointer.
 * @param clazz Java class reference.
 * @param handle Native address of the TerrainSystem.
 */
JNIEXPORT void JNICALL
Java_net_xmx_velthoric_jni_TerrainSystem_nDestroy(JNIEnv* env, jclass clazz, jlong handle) {
    (void)env; (void)clazz;
    delete reinterpret_cast<Velthoric::TerrainSystem*>(handle);
}

/**
 * @brief Requests a chunk, incrementing its reference count.
 *
 * @param env JNI environment pointer.
 * @param clazz Java class reference.
 * @param handle Native address of the TerrainSystem.
 * @param packedPos Bit-packed section coordinate.
 * @return True if this is a new chunk needing data.
 */
JNIEXPORT jboolean JNICALL
Java_net_xmx_velthoric_jni_TerrainSystem_nRequestChunk(JNIEnv* env, jclass clazz, jlong handle, jlong packedPos) {
    (void)env; (void)clazz;
    auto* system = reinterpret_cast<Velthoric::TerrainSystem*>(handle);
    return system ? system->RequestChunk(packedPos) : JNI_FALSE;
}

/**
 * @brief Releases a chunk, decrementing its reference count.
 *
 * @param env JNI environment pointer.
 * @param clazz Java class reference.
 * @param handle Native address of the TerrainSystem.
 * @param packedPos Bit-packed section coordinate.
 */
JNIEXPORT void JNICALL
Java_net_xmx_velthoric_jni_TerrainSystem_nReleaseChunk(JNIEnv* env, jclass clazz, jlong handle, jlong packedPos) {
    (void)env; (void)clazz;
    auto* system = reinterpret_cast<Velthoric::TerrainSystem*>(handle);
    if (system) system->ReleaseChunk(packedPos);
}

/**
 * @brief Activates a chunk in the physics simulation.
 *
 * @param env JNI environment pointer.
 * @param clazz Java class reference.
 * @param handle Native address of the TerrainSystem.
 * @param packedPos Bit-packed section coordinate.
 * @return True if the chunk is a placeholder and needs re-submission.
 */
JNIEXPORT jboolean JNICALL
Java_net_xmx_velthoric_jni_TerrainSystem_nActivateChunk(JNIEnv* env, jclass clazz, jlong handle, jlong packedPos) {
    (void)env; (void)clazz;
    auto* system = reinterpret_cast<Velthoric::TerrainSystem*>(handle);
    return system ? system->ActivateChunk(packedPos) : JNI_FALSE;
}

/**
 * @brief Deactivates a chunk, removing its body from the simulation.
 *
 * @param env JNI environment pointer.
 * @param clazz Java class reference.
 * @param handle Native address of the TerrainSystem.
 * @param packedPos Bit-packed section coordinate.
 */
JNIEXPORT void JNICALL
Java_net_xmx_velthoric_jni_TerrainSystem_nDeactivateChunk(JNIEnv* env, jclass clazz, jlong handle, jlong packedPos) {
    (void)env; (void)clazz;
    auto* system = reinterpret_cast<Velthoric::TerrainSystem*>(handle);
    if (system) system->DeactivateChunk(packedPos);
}

/**
 * @brief Checks if a chunk should be prioritized for data submission.
 *
 * @param env JNI environment pointer.
 * @param clazz Java class reference.
 * @param handle Native address of the TerrainSystem.
 * @param packedPos Bit-packed section coordinate.
 * @return True if the chunk needs data.
 */
JNIEXPORT jboolean JNICALL
Java_net_xmx_velthoric_jni_TerrainSystem_nPrioritizeChunk(JNIEnv* env, jclass clazz, jlong handle, jlong packedPos) {
    (void)env; (void)clazz;
    auto* system = reinterpret_cast<Velthoric::TerrainSystem*>(handle);
    return system ? system->PrioritizeChunk(packedPos) : JNI_FALSE;
}

/**
 * @brief Submits voxel box data for a chunk and creates/updates the physics body.
 *
 * @param env JNI environment pointer.
 * @param clazz Java class reference.
 * @param handle Native address of the TerrainSystem.
 * @param packedPos Bit-packed section coordinate.
 * @param posX World X coordinate for body placement.
 * @param posY World Y coordinate for body placement.
 * @param posZ World Z coordinate for body placement.
 * @param buffer DirectByteBuffer containing BoxShapeData structs.
 * @param boxCount Number of BoxShapeData structs in the buffer.
 * @param contentHash Unique hash for shape caching.
 * @param isInitialBuild True if this is the first build.
 * @return True if the chunk contains solid geometry.
 */
JNIEXPORT jboolean JNICALL
Java_net_xmx_velthoric_jni_TerrainSystem_nSubmitChunkData(JNIEnv* env, jclass clazz, jlong handle,
                                                           jlong packedPos, jfloat posX, jfloat posY, jfloat posZ,
                                                           jobject buffer, jint boxCount, jint contentHash,
                                                           jboolean isInitialBuild) {
    (void)clazz;
    auto* system = reinterpret_cast<Velthoric::TerrainSystem*>(handle);
    if (!system) return JNI_FALSE;

    const Velthoric::BoxShapeData* boxes = nullptr;
    if (buffer && boxCount > 0) {
        boxes = static_cast<const Velthoric::BoxShapeData*>(env->GetDirectBufferAddress(buffer));
    }

    return system->SubmitChunkData(packedPos, posX, posY, posZ, boxes, boxCount, contentHash, isInitialBuild);
}

/**
 * @brief Checks if a chunk is ready for physics.
 *
 * @param env JNI environment pointer.
 * @param clazz Java class reference.
 * @param handle Native address of the TerrainSystem.
 * @param packedPos Bit-packed section coordinate.
 * @return True if ready.
 */
JNIEXPORT jboolean JNICALL
Java_net_xmx_velthoric_jni_TerrainSystem_nIsReady(JNIEnv* env, jclass clazz, jlong handle, jlong packedPos) {
    (void)env; (void)clazz;
    auto* system = reinterpret_cast<Velthoric::TerrainSystem*>(handle);
    return system ? system->IsReady(packedPos) : JNI_FALSE;
}

/**
 * @brief Checks if a chunk is using a placeholder shape.
 *
 * @param env JNI environment pointer.
 * @param clazz Java class reference.
 * @param handle Native address of the TerrainSystem.
 * @param packedPos Bit-packed section coordinate.
 * @return True if placeholder.
 */
JNIEXPORT jboolean JNICALL
Java_net_xmx_velthoric_jni_TerrainSystem_nIsPlaceholder(JNIEnv* env, jclass clazz, jlong handle, jlong packedPos) {
    (void)env; (void)clazz;
    auto* system = reinterpret_cast<Velthoric::TerrainSystem*>(handle);
    return system ? system->IsPlaceholder(packedPos) : JNI_TRUE;
}

/**
 * @brief Checks if a chunk is managed by the system.
 *
 * @param env JNI environment pointer.
 * @param clazz Java class reference.
 * @param handle Native address of the TerrainSystem.
 * @param packedPos Bit-packed section coordinate.
 * @return True if managed.
 */
JNIEXPORT jboolean JNICALL
Java_net_xmx_velthoric_jni_TerrainSystem_nIsManaged(JNIEnv* env, jclass clazz, jlong handle, jlong packedPos) {
    (void)env; (void)clazz;
    auto* system = reinterpret_cast<Velthoric::TerrainSystem*>(handle);
    return system ? system->IsManaged(packedPos) : JNI_FALSE;
}

/**
 * @brief Checks if a body ID belongs to a terrain chunk.
 *
 * @param env JNI environment pointer.
 * @param clazz Java class reference.
 * @param handle Native address of the TerrainSystem.
 * @param bodyId The Jolt body ID.
 * @return True if terrain body.
 */
JNIEXPORT jboolean JNICALL
Java_net_xmx_velthoric_jni_TerrainSystem_nIsTerrainBody(JNIEnv* env, jclass clazz, jlong handle, jint bodyId) {
    (void)env; (void)clazz;
    auto* system = reinterpret_cast<Velthoric::TerrainSystem*>(handle);
    return system ? system->IsTerrainBody(static_cast<uint32_t>(bodyId)) : JNI_FALSE;
}

/**
 * @brief Returns packed positions of all active chunks as a Java long array.
 *
 * @param env JNI environment pointer.
 * @param clazz Java class reference.
 * @param handle Native address of the TerrainSystem.
 * @return A Java long array of packed positions, or null on failure.
 */
JNIEXPORT jlongArray JNICALL
Java_net_xmx_velthoric_jni_TerrainSystem_nGetActiveChunkPositions(JNIEnv* env, jclass clazz, jlong handle) {
    (void)clazz;
    auto* system = reinterpret_cast<Velthoric::TerrainSystem*>(handle);
    if (!system) return nullptr;

    std::vector<int64_t> positions = system->GetActiveChunkPositions();
    jlongArray result = env->NewLongArray(static_cast<jsize>(positions.size()));
    if (result && !positions.empty()) {
        env->SetLongArrayRegion(result, 0, static_cast<jsize>(positions.size()), positions.data());
    }
    return result;
}

/**
 * @brief Destroys all physics bodies managed by the terrain system.
 *
 * @param env JNI environment pointer.
 * @param clazz Java class reference.
 * @param handle Native address of the TerrainSystem.
 */
JNIEXPORT void JNICALL
Java_net_xmx_velthoric_jni_TerrainSystem_nCleanupAllBodies(JNIEnv* env, jclass clazz, jlong handle) {
    (void)env; (void)clazz;
    auto* system = reinterpret_cast<Velthoric::TerrainSystem*>(handle);
    if (system) system->CleanupAllBodies();
}

/**
 * @brief Clears the native terrain shape cache.
 *
 * @param env JNI environment pointer.
 * @param clazz Java class reference.
 */
JNIEXPORT void JNICALL
Java_net_xmx_velthoric_jni_TerrainSystem_nClearShapeCache(JNIEnv* env, jclass clazz) {
    (void)env; (void)clazz;
    Velthoric::TerrainGenerator::s_ShapeCache.Clear();
}

/**
 * @brief Registers the custom TerrainVoxelShape natively in the Jolt collision dispatcher.
 *
 * @param env JNI environment pointer.
 * @param clazz Java class reference.
 */
JNIEXPORT void JNICALL
Java_net_xmx_velthoric_jni_TerrainSystem_nRegisterVoxelShape(JNIEnv* env, jclass clazz) {
    (void)env; (void)clazz;
    Velthoric::TerrainVoxelShape::sRegister();
}

/**
 * @brief Registers a terrain material with specific friction and restitution.
 *
 * @param env JNI environment pointer.
 * @param clazz Reference to the Java class.
 * @param id Material ID (1-65535).
 * @param friction Material friction.
 * @param restitution Material restitution.
 */
JNIEXPORT void JNICALL
Java_net_xmx_velthoric_jni_TerrainSystem_nRegisterMaterial(JNIEnv *env, jclass clazz, jint id, jfloat friction, jfloat restitution) {
    (void)env; (void)clazz;
    if (id > 0 && id < 65536) {
        Velthoric::TerrainGenerator::s_Materials[id] = new Velthoric::TerrainMaterial(id, friction, restitution);
    }
}

/**
 * @brief JNI bridge for handling a block update.
 *
 * @param env JNI environment pointer.
 * @param clazz Java class reference.
 * @param handle Native address of the TerrainSystem.
 * @param x World X coordinate.
 * @param y World Y coordinate.
 * @param z World Z coordinate.
 */
JNIEXPORT void JNICALL
Java_net_xmx_velthoric_jni_TerrainSystem_nOnBlockUpdate(JNIEnv* env, jclass clazz, jlong handle, jint x, jint y, jint z) {
    (void)env; (void)clazz;
    auto* system = reinterpret_cast<Velthoric::TerrainSystem*>(handle);
    if (system) system->OnBlockUpdate(x, y, z);
}

} // extern "C"