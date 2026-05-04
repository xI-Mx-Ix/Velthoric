/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 *
 * Author xI-Mx-Ix
 */
#include "BodyPairIgnoreHandler.h"
#include <algorithm>

namespace Velthoric {

/**
 * @brief Constructs a new BodyPairIgnoreHandler.
 * Initializes the atomic flag to false indicating no initial ignored pairs.
 */
BodyPairIgnoreHandler::BodyPairIgnoreHandler() : m_HasIgnoredPairs(false) {
}

/**
 * @brief Default destructor.
 */
BodyPairIgnoreHandler::~BodyPairIgnoreHandler() {
}

/**
 * @brief Computes a normalized 64-bit key from two 32-bit body IDs.
 * 
 * Normalization (smaller ID in lower 32 bits, larger in upper 32 bits) ensures 
 * that the order of parameters does not affect the resulting key, making 
 * (A, B) and (B, A) identical in the underlying set.
 * 
 * @param bodyId1 First body ID.
 * @param bodyId2 Second body ID.
 * @return A unique 64-bit key representing the pair.
 */
uint64_t BodyPairIgnoreHandler::MakePairKey(uint32_t bodyId1, uint32_t bodyId2) {
    if (bodyId1 < bodyId2) {
        return (static_cast<uint64_t>(bodyId2) << 32) | static_cast<uint64_t>(bodyId1);
    } else {
        return (static_cast<uint64_t>(bodyId1) << 32) | static_cast<uint64_t>(bodyId2);
    }
}

/**
 * @brief Registers a body pair to be ignored during collision detection.
 * 
 * This operation is thread-safe. It updates both the pair set and the 
 * involved bodies set to enable optimized early-out checks.
 * 
 * @param bodyId1 First Jolt body ID.
 * @param bodyId2 Second Jolt body ID.
 */
void BodyPairIgnoreHandler::IgnorePair(uint32_t bodyId1, uint32_t bodyId2) {
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

/**
 * @brief Removes a body pair from the ignore list.
 * 
 * Normal collision behavior will be restored for this pair. The involved 
 * bodies set is updated by checking if either body is still part of other 
 * ignored pairs.
 * 
 * @param bodyId1 First Jolt body ID.
 * @param bodyId2 Second Jolt body ID.
 */
void BodyPairIgnoreHandler::RemoveIgnorePair(uint32_t bodyId1, uint32_t bodyId2) {
    uint64_t key = MakePairKey(bodyId1, bodyId2);
    std::lock_guard<std::mutex> lock(m_Mutex);
    m_IgnoredPairs.erase(key);

    // Recalculate if the bodies are still involved in ANY other ignored pair.
    // This maintains the correctness of the O(1) IsBodyInvolved fast path.
    bool body1StillInvolved = false;
    bool body2StillInvolved = false;
    for (const uint64_t pairKey : m_IgnoredPairs) {
        uint32_t b1 = static_cast<uint32_t>(pairKey & 0xFFFFFFFFULL);
        uint32_t b2 = static_cast<uint32_t>(pairKey >> 32);
        if (b1 == bodyId1 || b2 == bodyId1) body1StillInvolved = true;
        if (b1 == bodyId2 || b2 == bodyId2) body2StillInvolved = true;
        if (body1StillInvolved && body2StillInvolved) break;
    }

    if (!body1StillInvolved) m_InvolvedBodies.erase(bodyId1);
    if (!body2StillInvolved) m_InvolvedBodies.erase(bodyId2);

    if (m_IgnoredPairs.empty()) {
        m_HasIgnoredPairs.store(false, std::memory_order_release);
    }
}

/**
 * @brief Thread-safe check if a specific pair is currently ignored.
 * 
 * @param bodyId1 First Jolt body ID.
 * @param bodyId2 Second Jolt body ID.
 * @return True if collision should be ignored.
 */
bool BodyPairIgnoreHandler::IsPairIgnored(uint32_t bodyId1, uint32_t bodyId2) const {
    uint64_t key = MakePairKey(bodyId1, bodyId2);
    std::lock_guard<std::mutex> lock(m_Mutex);
    return m_IgnoredPairs.find(key) != m_IgnoredPairs.end();
}

/**
 * @brief Exports all currently ignored pairs into a flattened vector.
 * 
 * Used by the JNI bridge to synchronize state with the Java side.
 * 
 * @param outPairs Vector to populate with [ID1_a, ID1_b, ID2_a, ID2_b, ...].
 */
void BodyPairIgnoreHandler::GetIgnoredPairs(std::vector<uint32_t>& outPairs) const {
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

/**
 * @brief Cleanup hook triggered when a body is removed from the physics world.
 * 
 * Iterates through all ignored pairs and removes those containing the target ID.
 * This ensures no stale IDs remain in the filter sets.
 * 
 * @param bodyId The Jolt body ID being destroyed.
 */
void BodyPairIgnoreHandler::OnBodyRemoved(uint32_t bodyId) {
    std::lock_guard<std::mutex> lock(m_Mutex);
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
    m_InvolvedBodies.erase(bodyId);
    if (m_IgnoredPairs.empty()) {
        m_HasIgnoredPairs.store(false, std::memory_order_release);
    }
}

/**
 * @brief Purges all collision filters from the handler.
 */
void BodyPairIgnoreHandler::Clear() {
    std::lock_guard<std::mutex> lock(m_Mutex);
    m_IgnoredPairs.clear();
    m_InvolvedBodies.clear();
    m_HasIgnoredPairs.store(false, std::memory_order_release);
}

/**
 * @brief Returns the current number of ignored body pairs.
 * @return Size of the internal set.
 */
size_t BodyPairIgnoreHandler::Size() const {
    std::lock_guard<std::mutex> lock(m_Mutex);
    return m_IgnoredPairs.size();
}

/**
 * @brief Optimized lock-free check to determine if ANY pairs are ignored.
 * @return True if the ignore set is non-empty.
 */
bool BodyPairIgnoreHandler::HasIgnoredPairs() const {
    return m_HasIgnoredPairs.load(std::memory_order_acquire);
}

/**
 * @brief Fast O(1) check if a body index is part of any active filter.
 * @param bodyId Jolt body ID to check.
 * @return True if the body is involved in at least one ignore-pair.
 */
bool BodyPairIgnoreHandler::IsBodyInvolved(uint32_t bodyId) const {
    std::lock_guard<std::mutex> lock(m_Mutex);
    return m_InvolvedBodies.find(bodyId) != m_InvolvedBodies.end();
}

/**
 * @brief Integrated multi-stage validation check for the physics ContactListener.
 * 
 * This method is the primary hot-path entry point. It uses an atomic early-out
 * followed by an involved-set check before performing the final hash set lookup.
 * 
 * @param bodyId1 First colliding body ID.
 * @param bodyId2 Second colliding body ID.
 * @return True if the collision should be rejected.
 */
bool BodyPairIgnoreHandler::ShouldIgnorePair(uint32_t bodyId1, uint32_t bodyId2) const {
    // 1. Atomic early-out: No pairs ignored globally?
    if (!m_HasIgnoredPairs.load(std::memory_order_acquire)) {
        return false;
    }

    std::lock_guard<std::mutex> lock(m_Mutex);
    
    // 2. Double-check after lock (consistency)
    if (m_IgnoredPairs.empty()) return false;

    // 3. Fast O(1) involved check: Is either body even part of a filter?
    if (m_InvolvedBodies.find(bodyId1) == m_InvolvedBodies.end() &&
        m_InvolvedBodies.find(bodyId2) == m_InvolvedBodies.end()) {
        return false;
    }

    // 4. Final verification: Check specific pair key.
    uint64_t key = MakePairKey(bodyId1, bodyId2);
    return m_IgnoredPairs.find(key) != m_IgnoredPairs.end();
}

} // namespace Velthoric

#include <jni.h>

extern "C" {

/**
 * @brief JNI Bridge: Instantiates a new native BodyPairIgnoreHandler.
 * 
 * @param env JNI Environment.
 * @param clazz Calling Java class.
 * @return Address of the heap-allocated handler.
 */
JNIEXPORT jlong JNICALL
Java_net_xmx_velthoric_jni_BodyPairIgnoreHandler_nCreateHandler(JNIEnv* env, jclass clazz) {
    (void)env; (void)clazz;
    return reinterpret_cast<jlong>(new Velthoric::BodyPairIgnoreHandler());
}

/**
 * @brief JNI Bridge: Safely deletes the native BodyPairIgnoreHandler instance.
 * 
 * @param env JNI Environment.
 * @param clazz Calling Java class.
 * @param address Address of the native instance to destroy.
 */
JNIEXPORT void JNICALL
Java_net_xmx_velthoric_jni_BodyPairIgnoreHandler_nDestroyHandler(JNIEnv* env, jclass clazz, jlong address) {
    (void)env; (void)clazz;
    delete reinterpret_cast<Velthoric::BodyPairIgnoreHandler*>(address);
}

/**
 * @brief JNI Bridge: Adds a body pair to the ignore list via body IDs.
 */
JNIEXPORT void JNICALL
Java_net_xmx_velthoric_jni_BodyPairIgnoreHandler_nIgnorePair(JNIEnv* env, jclass clazz, jlong address, jint bodyId1, jint bodyId2) {
    (void)env; (void)clazz;
    auto* handler = reinterpret_cast<Velthoric::BodyPairIgnoreHandler*>(address);
    if (handler) handler->IgnorePair(static_cast<uint32_t>(bodyId1), static_cast<uint32_t>(bodyId2));
}

/**
 * @brief JNI Bridge: Removes a body pair from the ignore list via body IDs.
 */
JNIEXPORT void JNICALL
Java_net_xmx_velthoric_jni_BodyPairIgnoreHandler_nRemoveIgnorePair(JNIEnv* env, jclass clazz, jlong address, jint bodyId1, jint bodyId2) {
    (void)env; (void)clazz;
    auto* handler = reinterpret_cast<Velthoric::BodyPairIgnoreHandler*>(address);
    if (handler) handler->RemoveIgnorePair(static_cast<uint32_t>(bodyId1), static_cast<uint32_t>(bodyId2));
}

/**
 * @brief JNI Bridge: Checks if a pair is currently ignored.
 */
JNIEXPORT jboolean JNICALL
Java_net_xmx_velthoric_jni_BodyPairIgnoreHandler_nIsPairIgnored(JNIEnv* env, jclass clazz, jlong address, jint bodyId1, jint bodyId2) {
    (void)env; (void)clazz;
    auto* handler = reinterpret_cast<Velthoric::BodyPairIgnoreHandler*>(address);
    return (handler && handler->IsPairIgnored(static_cast<uint32_t>(bodyId1), static_cast<uint32_t>(bodyId2))) ? JNI_TRUE : JNI_FALSE;
}

/**
 * @brief JNI Bridge: Retrieves all ignored pairs as a flattened int array.
 */
JNIEXPORT jintArray JNICALL
Java_net_xmx_velthoric_jni_BodyPairIgnoreHandler_nGetIgnoredPairs(JNIEnv* env, jclass clazz, jlong address) {
    (void)clazz;
    auto* handler = reinterpret_cast<Velthoric::BodyPairIgnoreHandler*>(address);
    if (!handler) return nullptr;

    std::vector<uint32_t> pairs;
    handler->GetIgnoredPairs(pairs);
    if (pairs.empty()) return nullptr;

    jintArray result = env->NewIntArray(static_cast<jsize>(pairs.size()));
    if (result) env->SetIntArrayRegion(result, 0, static_cast<jsize>(pairs.size()), reinterpret_cast<const jint*>(pairs.data()));
    return result;
}

/**
 * @brief JNI Bridge: Notifies the handler that a body was removed.
 */
JNIEXPORT void JNICALL
Java_net_xmx_velthoric_jni_BodyPairIgnoreHandler_nOnBodyRemoved(JNIEnv* env, jclass clazz, jlong address, jint bodyId) {
    (void)env; (void)clazz;
    auto* handler = reinterpret_cast<Velthoric::BodyPairIgnoreHandler*>(address);
    if (handler) handler->OnBodyRemoved(static_cast<uint32_t>(bodyId));
}

/**
 * @brief JNI Bridge: Clears all active collision filters.
 */
JNIEXPORT void JNICALL
Java_net_xmx_velthoric_jni_BodyPairIgnoreHandler_nClear(JNIEnv* env, jclass clazz, jlong address) {
    (void)env; (void)clazz;
    auto* handler = reinterpret_cast<Velthoric::BodyPairIgnoreHandler*>(address);
    if (handler) handler->Clear();
}

/**
 * @brief JNI Bridge: Returns the current number of collision filters.
 */
JNIEXPORT jint JNICALL
Java_net_xmx_velthoric_jni_BodyPairIgnoreHandler_nSize(JNIEnv* env, jclass clazz, jlong address) {
    (void)env; (void)clazz;
    auto* handler = reinterpret_cast<Velthoric::BodyPairIgnoreHandler*>(address);
    return handler ? static_cast<jint>(handler->Size()) : 0;
}

}