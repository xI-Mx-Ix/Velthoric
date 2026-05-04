/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 *
 * Author xI-Mx-Ix
 */
#include "BodyPairIgnoreManager.h"
#include <algorithm>

namespace Velthoric {

BodyPairIgnoreManager::BodyPairIgnoreManager() : m_HasIgnoredPairs(false) {
}

BodyPairIgnoreManager::~BodyPairIgnoreManager() {
}

uint64_t BodyPairIgnoreManager::MakePairKey(uint32_t bodyId1, uint32_t bodyId2) {
    // Normalize the pair: smaller ID in lower 32 bits, larger in upper 32 bits
    // This ensures (a, b) and (b, a) produce the same key
    if (bodyId1 < bodyId2) {
        return (static_cast<uint64_t>(bodyId2) << 32) | static_cast<uint64_t>(bodyId1);
    } else {
        return (static_cast<uint64_t>(bodyId1) << 32) | static_cast<uint64_t>(bodyId2);
    }
}

void BodyPairIgnoreManager::IgnorePair(uint32_t bodyId1, uint32_t bodyId2) {
    uint64_t key = MakePairKey(bodyId1, bodyId2);
    std::lock_guard<std::mutex> lock(m_Mutex);
    bool wasEmpty = m_IgnoredPairs.empty();
    m_IgnoredPairs.insert(key);
    m_InvolvedBodies.insert(bodyId1);
    m_InvolvedBodies.insert(bodyId2);
    if (wasEmpty) {
        m_HasIgnoredPairs.store(true, std::memory_order_release);
    }
}

void BodyPairIgnoreManager::RemoveIgnorePair(uint32_t bodyId1, uint32_t bodyId2) {
    uint64_t key = MakePairKey(bodyId1, bodyId2);
    std::lock_guard<std::mutex> lock(m_Mutex);
    m_IgnoredPairs.erase(key);

    // Remove bodies from involved set only if they are not in any other ignored pairs
    bool body1StillInvolved = false;
    bool body2StillInvolved = false;
    for (const uint64_t pairKey : m_IgnoredPairs) {
        uint32_t b1 = static_cast<uint32_t>(pairKey & 0xFFFFFFFFULL);
        uint32_t b2 = static_cast<uint32_t>(pairKey >> 32);
        if (b1 == bodyId1 || b2 == bodyId1) body1StillInvolved = true;
        if (b1 == bodyId2 || b2 == bodyId2) body2StillInvolved = true;
    }

    if (!body1StillInvolved) m_InvolvedBodies.erase(bodyId1);
    if (!body2StillInvolved) m_InvolvedBodies.erase(bodyId2);

    if (m_IgnoredPairs.empty()) {
        m_HasIgnoredPairs.store(false, std::memory_order_release);
    }
}

bool BodyPairIgnoreManager::IsPairIgnored(uint32_t bodyId1, uint32_t bodyId2) const {
    uint64_t key = MakePairKey(bodyId1, bodyId2);
    std::lock_guard<std::mutex> lock(m_Mutex);
    return m_IgnoredPairs.find(key) != m_IgnoredPairs.end();
}

void BodyPairIgnoreManager::GetIgnoredPairs(std::vector<uint32_t>& outPairs) const {
    std::lock_guard<std::mutex> lock(m_Mutex);
    outPairs.clear();
    outPairs.reserve(m_IgnoredPairs.size() * 2);
    
    for (const uint64_t key : m_IgnoredPairs) {
        uint32_t bodyId1 = static_cast<uint32_t>(key & 0xFFFFFFFFULL);
        uint32_t bodyId2 = static_cast<uint32_t>(key >> 32);
        outPairs.push_back(bodyId1);
        outPairs.push_back(bodyId2);
    }
}

void BodyPairIgnoreManager::OnBodyRemoved(uint32_t bodyId) {
    std::lock_guard<std::mutex> lock(m_Mutex);

    // Remove all pairs that involve the removed body ID
    // We need to check both positions since the key is normalized
    auto it = m_IgnoredPairs.begin();
    while (it != m_IgnoredPairs.end()) {
        uint64_t key = *it;
        uint32_t bodyId1 = static_cast<uint32_t>(key & 0xFFFFFFFFULL);
        uint32_t bodyId2 = static_cast<uint32_t>(key >> 32);

        if (bodyId1 == bodyId || bodyId2 == bodyId) {
            it = m_IgnoredPairs.erase(it);
        } else {
            ++it;
        }
    }

    // Remove the body from the involved set
    m_InvolvedBodies.erase(bodyId);

    if (m_IgnoredPairs.empty()) {
        m_HasIgnoredPairs.store(false, std::memory_order_release);
    }
}

void BodyPairIgnoreManager::Clear() {
    std::lock_guard<std::mutex> lock(m_Mutex);
    m_IgnoredPairs.clear();
    m_InvolvedBodies.clear();
    m_HasIgnoredPairs.store(false, std::memory_order_release);
}

size_t BodyPairIgnoreManager::Size() const {
    std::lock_guard<std::mutex> lock(m_Mutex);
    return m_IgnoredPairs.size();
}

bool BodyPairIgnoreManager::HasIgnoredPairs() const {
    std::lock_guard<std::mutex> lock(m_Mutex);
    return !m_IgnoredPairs.empty();
}

bool BodyPairIgnoreManager::IsBodyInvolved(uint32_t bodyId) const {
    std::lock_guard<std::mutex> lock(m_Mutex);
    return m_InvolvedBodies.find(bodyId) != m_InvolvedBodies.end();
}

bool BodyPairIgnoreManager::ShouldIgnorePair(uint32_t bodyId1, uint32_t bodyId2) const {
    // Lock-free fast path: no ignored pairs at all
    if (!m_HasIgnoredPairs.load(std::memory_order_acquire)) {
        return false;
    }

    std::lock_guard<std::mutex> lock(m_Mutex);

    // Double-check after acquiring lock (in case it changed)
    if (m_IgnoredPairs.empty()) {
        return false;
    }

    // Fast path: neither body is involved in any ignored pairs
    if (m_InvolvedBodies.find(bodyId1) == m_InvolvedBodies.end() &&
        m_InvolvedBodies.find(bodyId2) == m_InvolvedBodies.end()) {
        return false;
    }

    // Full check: see if this specific pair is ignored
    uint64_t key = MakePairKey(bodyId1, bodyId2);
    return m_IgnoredPairs.find(key) != m_IgnoredPairs.end();
}

} // namespace Velthoric

// JNI Bindings
#include <jni.h>

/**
 * @brief JNI Bridge: Instantiates a new native BodyPairIgnoreManager.
 * 
 * @param env The JNI Environment pointer.
 * @param clazz The calling Java class.
 * @return The virtual address of the newly allocated manager.
 */
extern "C" JNIEXPORT jlong JNICALL
Java_net_xmx_velthoric_jni_BodyPairIgnoreManager_nCreateManager(JNIEnv* env, jclass clazz) {
    (void)env;
    (void)clazz;
    auto* manager = new Velthoric::BodyPairIgnoreManager();
    return reinterpret_cast<jlong>(manager);
}

/**
 * @brief JNI Bridge: Safely deletes the native BodyPairIgnoreManager.
 * 
 * @param env The JNI Environment pointer.
 * @param clazz The calling Java class.
 * @param address The virtual address of the manager to destroy.
 */
extern "C" JNIEXPORT void JNICALL
Java_net_xmx_velthoric_jni_BodyPairIgnoreManager_nDestroyManager(JNIEnv* env, jclass clazz, jlong address) {
    (void)env;
    (void)clazz;
    Velthoric::BodyPairIgnoreManager* manager = reinterpret_cast<Velthoric::BodyPairIgnoreManager*>(address);
    delete manager;
}

/**
 * @brief JNI Bridge: Adds a body pair to the ignore list.
 * 
 * @param env The JNI Environment pointer.
 * @param clazz The calling Java class.
 * @param address The virtual address of the manager.
 * @param bodyId1 The first Jolt body ID.
 * @param bodyId2 The second Jolt body ID.
 */
extern "C" JNIEXPORT void JNICALL
Java_net_xmx_velthoric_jni_BodyPairIgnoreManager_nIgnorePair(JNIEnv* env, jclass clazz, jlong address, jint bodyId1, jint bodyId2) {
    (void)env;
    (void)clazz;
    Velthoric::BodyPairIgnoreManager* manager = reinterpret_cast<Velthoric::BodyPairIgnoreManager*>(address);
    if (manager) {
        manager->IgnorePair(static_cast<uint32_t>(bodyId1), static_cast<uint32_t>(bodyId2));
    }
}

/**
 * @brief JNI Bridge: Removes a body pair from the ignore list.
 * 
 * @param env The JNI Environment pointer.
 * @param clazz The calling Java class.
 * @param address The virtual address of the manager.
 * @param bodyId1 The first Jolt body ID.
 * @param bodyId2 The second Jolt body ID.
 */
extern "C" JNIEXPORT void JNICALL
Java_net_xmx_velthoric_jni_BodyPairIgnoreManager_nRemoveIgnorePair(JNIEnv* env, jclass clazz, jlong address, jint bodyId1, jint bodyId2) {
    (void)env;
    (void)clazz;
    Velthoric::BodyPairIgnoreManager* manager = reinterpret_cast<Velthoric::BodyPairIgnoreManager*>(address);
    if (manager) {
        manager->RemoveIgnorePair(static_cast<uint32_t>(bodyId1), static_cast<uint32_t>(bodyId2));
    }
}

/**
 * @brief JNI Bridge: Checks if a body pair is ignored.
 * 
 * @param env The JNI Environment pointer.
 * @param clazz The calling Java class.
 * @param address The virtual address of the manager.
 * @param bodyId1 The first Jolt body ID.
 * @param bodyId2 The second Jolt body ID.
 * @return JNI_TRUE if ignored, JNI_FALSE otherwise.
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_net_xmx_velthoric_jni_BodyPairIgnoreManager_nIsPairIgnored(JNIEnv* env, jclass clazz, jlong address, jint bodyId1, jint bodyId2) {
    (void)env;
    (void)clazz;
    Velthoric::BodyPairIgnoreManager* manager = reinterpret_cast<Velthoric::BodyPairIgnoreManager*>(address);
    if (manager) {
        return manager->IsPairIgnored(static_cast<uint32_t>(bodyId1), static_cast<uint32_t>(bodyId2)) ? JNI_TRUE : JNI_FALSE;
    }
    return JNI_FALSE;
}

/**
 * @brief JNI Bridge: Returns all ignored pairs as a flattened int array.
 * 
 * @param env The JNI Environment pointer.
 * @param clazz The calling Java class.
 * @param address The virtual address of the manager.
 * @return A jintArray containing pairs: [b1_1, b1_2, b2_1, b2_2, ...].
 */
extern "C" JNIEXPORT jintArray JNICALL
Java_net_xmx_velthoric_jni_BodyPairIgnoreManager_nGetIgnoredPairs(JNIEnv* env, jclass clazz, jlong address) {
    (void)clazz;
    Velthoric::BodyPairIgnoreManager* manager = reinterpret_cast<Velthoric::BodyPairIgnoreManager*>(address);
    if (!manager) {
        return nullptr;
    }

    std::vector<uint32_t> pairs;
    manager->GetIgnoredPairs(pairs);

    if (pairs.empty()) {
        return nullptr;
    }

    jintArray result = env->NewIntArray(static_cast<jsize>(pairs.size()));
    if (result) {
        env->SetIntArrayRegion(result, 0, static_cast<jsize>(pairs.size()), reinterpret_cast<const jint*>(pairs.data()));
    }

    return result;
}

/**
 * @brief JNI Bridge: Notifies the manager that a body was removed.
 * 
 * @param env The JNI Environment pointer.
 * @param clazz The calling Java class.
 * @param address The virtual address of the manager.
 * @param bodyId The Jolt body ID that was removed.
 */
extern "C" JNIEXPORT void JNICALL
Java_net_xmx_velthoric_jni_BodyPairIgnoreManager_nOnBodyRemoved(JNIEnv* env, jclass clazz, jlong address, jint bodyId) {
    (void)env;
    (void)clazz;
    Velthoric::BodyPairIgnoreManager* manager = reinterpret_cast<Velthoric::BodyPairIgnoreManager*>(address);
    if (manager) {
        manager->OnBodyRemoved(static_cast<uint32_t>(bodyId));
    }
}

/**
 * @brief JNI Bridge: Clears all ignored pairs.
 * 
 * @param env The JNI Environment pointer.
 * @param clazz The calling Java class.
 * @param address The virtual address of the manager.
 */
extern "C" JNIEXPORT void JNICALL
Java_net_xmx_velthoric_jni_BodyPairIgnoreManager_nClear(JNIEnv* env, jclass clazz, jlong address) {
    (void)env;
    (void)clazz;
    Velthoric::BodyPairIgnoreManager* manager = reinterpret_cast<Velthoric::BodyPairIgnoreManager*>(address);
    if (manager) {
        manager->Clear();
    }
}

/**
 * @brief JNI Bridge: Returns the number of ignored pairs.
 * 
 * @param env The JNI Environment pointer.
 * @param clazz The calling Java class.
 * @param address The virtual address of the manager.
 * @return The count of ignored pairs.
 */
extern "C" JNIEXPORT jint JNICALL
Java_net_xmx_velthoric_jni_BodyPairIgnoreManager_nSize(JNIEnv* env, jclass clazz, jlong address) {
    (void)env;
    (void)clazz;
    Velthoric::BodyPairIgnoreManager* manager = reinterpret_cast<Velthoric::BodyPairIgnoreManager*>(address);
    if (manager) {
        return static_cast<jint>(manager->Size());
    }
    return 0;
}

/**
 * @brief JNI Bridge: Checks if there are any ignored pairs.
 * 
 * @param env The JNI Environment pointer.
 * @param clazz The calling Java class.
 * @param address The virtual address of the manager.
 * @return JNI_TRUE if any exist, JNI_FALSE otherwise.
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_net_xmx_velthoric_jni_BodyPairIgnoreManager_nHasIgnoredPairs(JNIEnv* env, jclass clazz, jlong address) {
    (void)env;
    (void)clazz;
    Velthoric::BodyPairIgnoreManager* manager = reinterpret_cast<Velthoric::BodyPairIgnoreManager*>(address);
    if (manager) {
        return manager->HasIgnoredPairs() ? JNI_TRUE : JNI_FALSE;
    }
    return JNI_FALSE;
}

/**
 * @brief JNI Bridge: Checks if a body is involved in any ignored pair.
 * 
 * @param env The JNI Environment pointer.
 * @param clazz The calling Java class.
 * @param address The virtual address of the manager.
 * @param bodyId The Jolt body ID to check.
 * @return JNI_TRUE if involved, JNI_FALSE otherwise.
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_net_xmx_velthoric_jni_BodyPairIgnoreManager_nIsBodyInvolved(JNIEnv* env, jclass clazz, jlong address, jint bodyId) {
    (void)env;
    (void)clazz;
    Velthoric::BodyPairIgnoreManager* manager = reinterpret_cast<Velthoric::BodyPairIgnoreManager*>(address);
    if (manager) {
        return manager->IsBodyInvolved(static_cast<uint32_t>(bodyId)) ? JNI_TRUE : JNI_FALSE;
    }
    return JNI_FALSE;
}

/**
 * @brief JNI Bridge: Performs an optimized single-lock check for contact filtering.
 * 
 * @param env The JNI Environment pointer.
 * @param clazz The calling Java class.
 * @param address The virtual address of the manager.
 * @param bodyId1 The first Jolt body ID.
 * @param bodyId2 The second Jolt body ID.
 * @return JNI_TRUE if collision should be ignored, JNI_FALSE otherwise.
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_net_xmx_velthoric_jni_BodyPairIgnoreManager_nShouldIgnorePair(JNIEnv* env, jclass clazz, jlong address, jint bodyId1, jint bodyId2) {
    (void)env;
    (void)clazz;
    Velthoric::BodyPairIgnoreManager* manager = reinterpret_cast<Velthoric::BodyPairIgnoreManager*>(address);
    if (manager) {
        return manager->ShouldIgnorePair(static_cast<uint32_t>(bodyId1), static_cast<uint32_t>(bodyId2)) ? JNI_TRUE : JNI_FALSE;
    }
    return JNI_FALSE;
}