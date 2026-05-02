/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 *
 * Author: xI-Mx-Ix
 */
#include "net_xmx_velthoric_jni_BatchPhysicsSync.h"
#include <Jolt/Jolt.h>
#include <Jolt/Physics/PhysicsSystem.h>
#include <Jolt/Physics/Body/BodyInterface.h>
#include <Jolt/Physics/Body/BodyLockInterface.h>
#include <Jolt/Physics/Body/Body.h>
#include <Jolt/Physics/SoftBody/SoftBodyMotionProperties.h>
#include <cmath>
#include <algorithm>

using namespace JPH;

namespace Velthoric {

/**
 * Synchronizes the native Jolt simulation results with the Java-side SoA data store.
 * 
 * This function performs a high-performance synchronization of:
 * - World-space positions (double[3])
 * - Rotations (float[4] quaternions)
 * - Linear and angular velocities (float[3])
 * - World-space Axis-Aligned Bounding Boxes (AABB)
 * - Activity states and last update timestamps
 * - Native motion types (Static, Kinematic, Dynamic)
 * - Soft body vertex locations (float[] per body)
 *
 * It utilizes JNI Critical regions for maximum throughput when processing large batches
 * of rigid bodies. Soft body vertex extraction is handled in a secondary pass to 
 * safely manage Java object array access.
 *
 * @param env Pointer to the JNI environment.
 * @param thiz Reference to the Java BatchPhysicsSync instance.
 * @param physicsSystemPtr Native pointer (long) to the Jolt PhysicsSystem.
 * @param count Number of bodies in the current batch.
 * @param indicesArr Java int[] containing the target indices in the SoA store.
 * @param bodyIdsArr Java int[] containing the Jolt BodyIDs.
 * @param behaviorBitsArr Java long[] containing the behavior bitmasks for each body.
 * @param posXArr Java double[] for X-positions.
 * @param posYArr Java double[] for Y-positions.
 * @param posZArr Java double[] for Z-positions.
 * @param rotXArr Java float[] for rotation X.
 * @param rotYArr Java float[] for rotation Y.
 * @param rotZArr Java float[] for rotation Z.
 * @param rotWArr Java float[] for rotation W.
 * @param velXArr Java float[] for linear velocity X.
 * @param velYArr Java float[] for linear velocity Y.
 * @param velZArr Java float[] for linear velocity Z.
 * @param angVelXArr Java float[] for angular velocity X.
 * @param angVelYArr Java float[] for angular velocity Y.
 * @param angVelZArr Java float[] for angular velocity Z.
 * @param aabbMinXArr Java float[] for AABB minimum X.
 * @param aabbMinYArr Java float[] for AABB minimum Y.
 * @param aabbMinZArr Java float[] for AABB minimum Z.
 * @param aabbMaxXArr Java float[] for AABB maximum X.
 * @param aabbMaxYArr Java float[] for AABB maximum Y.
 * @param aabbMaxZArr Java float[] for AABB maximum Z.
 * @param isActiveArr Java boolean[] for activity state.
 * @param isTransformDirtyArr Java boolean[] to mark transform updates for networking.
 * @param isVertexDataDirtyArr Java boolean[] to mark vertex updates for networking.
 * @param lastUpdateTimestampArr Java long[] for simulation timestamps.
 * @param motionTypeOutputArr Java byte[] to store native EMotionType ordinals.
 * @param dirtyIndicesOutputArr Java int[] to collect indices that need network broadcasting.
 * @param vertexDataArr Java float[][] containing vertex data for soft bodies.
 * @param softBodyBehaviorMask Bitmask identifying the SoftPhysics behavior.
 * @param timestampNanos The current simulation timestamp in nanoseconds.
 * @return The number of dirty indices written to dirtyIndicesOutputArr.
 */
extern "C" JNIEXPORT jint JNICALL Java_net_xmx_velthoric_jni_BatchPhysicsSync_syncPhysicsNative(
    JNIEnv* env, jclass clazz,
    jlong physicsSystemPtr,
    jint count,
    jintArray indicesArr,
    jintArray bodyIdsArr,
    jlongArray behaviorBitsArr,
    jdoubleArray posXArr, jdoubleArray posYArr, jdoubleArray posZArr,
    jfloatArray rotXArr, jfloatArray rotYArr, jfloatArray rotZArr, jfloatArray rotWArr,
    jfloatArray velXArr, jfloatArray velYArr, jfloatArray velZArr,
    jfloatArray angVelXArr, jfloatArray angVelYArr, jfloatArray angVelZArr,
    jfloatArray aabbMinXArr, jfloatArray aabbMinYArr, jfloatArray aabbMinZArr,
    jfloatArray aabbMaxXArr, jfloatArray aabbMaxYArr, jfloatArray aabbMaxZArr,
    jbooleanArray isActiveArr,
    jbooleanArray isTransformDirtyArr,
    jbooleanArray isVertexDataDirtyArr,
    jlongArray lastUpdateTimestampArr,
    jbyteArray motionTypeOutputArr,
    jintArray dirtyIndicesOutputArr,
    jobjectArray vertexDataArr,
    jlong softBodyBehaviorMask,
    jlong timestampNanos
) {
    if (physicsSystemPtr == 0) return 0;

    PhysicsSystem* physicsSystem = reinterpret_cast<PhysicsSystem*>(physicsSystemPtr);
    const BodyInterface& bodyInterface = physicsSystem->GetBodyInterfaceNoLock();
    const BodyLockInterface& lockInterface = physicsSystem->GetBodyLockInterfaceNoLock();

    // Batch tracking for soft bodies to handle them in a separate pass.
    int softBodyBatchIndices[512]; 
    int softBodyCount = 0;
    
    // Pass 1: JNI Critical section for high-speed transform synchronization.
    // We avoid calling any JNI functions that could trigger GC during this block.
    {
        jint* indices = (jint*)env->GetPrimitiveArrayCritical(indicesArr, nullptr);
        jint* bodyIds = (jint*)env->GetPrimitiveArrayCritical(bodyIdsArr, nullptr);
        jlong* behaviorBits = (jlong*)env->GetPrimitiveArrayCritical(behaviorBitsArr, nullptr);
        
        jdouble* posX = (jdouble*)env->GetPrimitiveArrayCritical(posXArr, nullptr);
        jdouble* posY = (jdouble*)env->GetPrimitiveArrayCritical(posYArr, nullptr);
        jdouble* posZ = (jdouble*)env->GetPrimitiveArrayCritical(posZArr, nullptr);
        
        jfloat* rotX = (jfloat*)env->GetPrimitiveArrayCritical(rotXArr, nullptr);
        jfloat* rotY = (jfloat*)env->GetPrimitiveArrayCritical(rotYArr, nullptr);
        jfloat* rotZ = (jfloat*)env->GetPrimitiveArrayCritical(rotZArr, nullptr);
        jfloat* rotW = (jfloat*)env->GetPrimitiveArrayCritical(rotWArr, nullptr);
        
        jfloat* velX = (jfloat*)env->GetPrimitiveArrayCritical(velXArr, nullptr);
        jfloat* velY = (jfloat*)env->GetPrimitiveArrayCritical(velYArr, nullptr);
        jfloat* velZ = (jfloat*)env->GetPrimitiveArrayCritical(velZArr, nullptr);
        
        jfloat* angVelX = (jfloat*)env->GetPrimitiveArrayCritical(angVelXArr, nullptr);
        jfloat* angVelY = (jfloat*)env->GetPrimitiveArrayCritical(angVelYArr, nullptr);
        jfloat* angVelZ = (jfloat*)env->GetPrimitiveArrayCritical(angVelZArr, nullptr);
        
        jfloat* aabbMinX = (jfloat*)env->GetPrimitiveArrayCritical(aabbMinXArr, nullptr);
        jfloat* aabbMinY = (jfloat*)env->GetPrimitiveArrayCritical(aabbMinYArr, nullptr);
        jfloat* aabbMinZ = (jfloat*)env->GetPrimitiveArrayCritical(aabbMinZArr, nullptr);
        
        jfloat* aabbMaxX = (jfloat*)env->GetPrimitiveArrayCritical(aabbMaxXArr, nullptr);
        jfloat* aabbMaxY = (jfloat*)env->GetPrimitiveArrayCritical(aabbMaxYArr, nullptr);
        jfloat* aabbMaxZ = (jfloat*)env->GetPrimitiveArrayCritical(aabbMaxZArr, nullptr);
        
        jboolean* isActive = (jboolean*)env->GetPrimitiveArrayCritical(isActiveArr, nullptr);
        jboolean* isTransformDirty = (jboolean*)env->GetPrimitiveArrayCritical(isTransformDirtyArr, nullptr);
        jlong* lastUpdateTimestamp = (jlong*)env->GetPrimitiveArrayCritical(lastUpdateTimestampArr, nullptr);
        jbyte* motionTypes = (jbyte*)env->GetPrimitiveArrayCritical(motionTypeOutputArr, nullptr);

        for (int b = 0; b < count; ++b) {
            BodyID id(bodyIds[b]);
            if (!bodyInterface.IsAdded(id)) continue;

            int i = indices[b];
            
            bool isJoltBodyActive = bodyInterface.IsActive(id);
            bool wasDataStoreBodyActive = (isActive[i] != 0);

            if (isJoltBodyActive || wasDataStoreBodyActive) {
                RVec3 bodyPos = bodyInterface.GetPosition(id);
                Quat rot = bodyInterface.GetRotation(id);
                Vec3 linVel = bodyInterface.GetLinearVelocity(id);
                Vec3 angVel = bodyInterface.GetAngularVelocity(id);

                if (isJoltBodyActive || isJoltBodyActive != wasDataStoreBodyActive) {
                    isTransformDirty[i] = 1;
                }

                posX[i] = bodyPos.GetX();
                posY[i] = bodyPos.GetY();
                posZ[i] = bodyPos.GetZ();

                rotX[i] = rot.GetX();
                rotY[i] = rot.GetY();
                rotZ[i] = rot.GetZ();
                rotW[i] = rot.GetW();

                velX[i] = linVel.GetX();
                velY[i] = linVel.GetY();
                velZ[i] = linVel.GetZ();

                angVelX[i] = angVel.GetX();
                angVelY[i] = angVel.GetY();
                angVelZ[i] = angVel.GetZ();

                isActive[i] = isJoltBodyActive ? 1 : 0;
                lastUpdateTimestamp[i] = timestampNanos;
                motionTypes[b] = (jbyte)bodyInterface.GetMotionType(id);

                {
                    BodyLockRead lock(lockInterface, id);
                    if (lock.Succeeded()) {
                        const Body& body = lock.GetBody();
                        AABox bounds = body.GetWorldSpaceBounds();
                        Vec3 min = bounds.mMin;
                        Vec3 max = bounds.mMax;

                        aabbMinX[i] = (float)min.GetX();
                        aabbMinY[i] = (float)min.GetY();
                        aabbMinZ[i] = (float)min.GetZ();
                        aabbMaxX[i] = (float)max.GetX();
                        aabbMaxY[i] = (float)max.GetY();
                        aabbMaxZ[i] = (float)max.GetZ();

                        // Flag soft bodies for separate vertex pass.
                        if (isJoltBodyActive && (behaviorBits[b] & softBodyBehaviorMask) != 0 && body.GetBodyType() == EBodyType::SoftBody) {
                            softBodyBatchIndices[softBodyCount++] = b;
                        }
                    }
                }
            }
        }

        env->ReleasePrimitiveArrayCritical(motionTypeOutputArr, motionTypes, 0);
        env->ReleasePrimitiveArrayCritical(lastUpdateTimestampArr, lastUpdateTimestamp, 0);
        env->ReleasePrimitiveArrayCritical(isTransformDirtyArr, isTransformDirty, 0);
        env->ReleasePrimitiveArrayCritical(isActiveArr, isActive, 0);
        env->ReleasePrimitiveArrayCritical(aabbMaxZArr, aabbMaxZ, 0);
        env->ReleasePrimitiveArrayCritical(aabbMaxYArr, aabbMaxY, 0);
        env->ReleasePrimitiveArrayCritical(aabbMaxXArr, aabbMaxX, 0);
        env->ReleasePrimitiveArrayCritical(aabbMinZArr, aabbMinZ, 0);
        env->ReleasePrimitiveArrayCritical(aabbMinYArr, aabbMinY, 0);
        env->ReleasePrimitiveArrayCritical(aabbMinXArr, aabbMinX, 0);
        env->ReleasePrimitiveArrayCritical(angVelZArr, angVelZ, 0);
        env->ReleasePrimitiveArrayCritical(angVelYArr, angVelY, 0);
        env->ReleasePrimitiveArrayCritical(angVelXArr, angVelX, 0);
        env->ReleasePrimitiveArrayCritical(velZArr, velZ, 0);
        env->ReleasePrimitiveArrayCritical(velYArr, velY, 0);
        env->ReleasePrimitiveArrayCritical(velXArr, velX, 0);
        env->ReleasePrimitiveArrayCritical(rotWArr, rotW, 0);
        env->ReleasePrimitiveArrayCritical(rotZArr, rotZ, 0);
        env->ReleasePrimitiveArrayCritical(rotYArr, rotY, 0);
        env->ReleasePrimitiveArrayCritical(rotXArr, rotX, 0);
        env->ReleasePrimitiveArrayCritical(posZArr, posZ, 0);
        env->ReleasePrimitiveArrayCritical(posYArr, posY, 0);
        env->ReleasePrimitiveArrayCritical(posXArr, posX, 0);
        env->ReleasePrimitiveArrayCritical(behaviorBitsArr, behaviorBits, 0);
        env->ReleasePrimitiveArrayCritical(bodyIdsArr, bodyIds, 0);
        env->ReleasePrimitiveArrayCritical(indicesArr, indices, 0);
    }

    int vertexDirtyIndices[512];
    int vertexDirtyCount = 0;

    // Pass 2: Soft Body Vertex Extraction.
    // Uses standard JNI calls to safely access the Java object array (float[][]).
    if (softBodyCount > 0) {
        for (int s = 0; s < softBodyCount; ++s) {
            int b = softBodyBatchIndices[s];
            
            jint i;
            env->GetIntArrayRegion(indicesArr, b, 1, &i);
            jint bodyIdInt;
            env->GetIntArrayRegion(bodyIdsArr, b, 1, &bodyIdInt);
            
            BodyID id(bodyIdInt);
            BodyLockRead lock(lockInterface, id);
            if (lock.Succeeded()) {
                const Body& body = lock.GetBody();
                const SoftBodyMotionProperties* mp = static_cast<const SoftBodyMotionProperties*>(body.GetMotionProperties());
                const Array<SoftBodyVertex>& vertices = mp->GetVertices();
                int numVertices = (int)vertices.size();
                int requiredFloats = numVertices * 3;

                jfloatArray innerArr = (jfloatArray)env->GetObjectArrayElement(vertexDataArr, i);
                bool newlyAllocated = false;
                if (innerArr == nullptr || env->GetArrayLength(innerArr) != requiredFloats) {
                    innerArr = env->NewFloatArray(requiredFloats);
                    env->SetObjectArrayElement(vertexDataArr, i, innerArr);
                    newlyAllocated = true;
                }

                jfloat* javaVertices = env->GetFloatArrayElements(innerArr, nullptr);
                RVec3 bodyPos = body.GetPosition();
                
                bool changed = newlyAllocated;
                for (int v = 0; v < numVertices; ++v) {
                    Vec3 worldPos = Vec3(bodyPos + vertices[v].mPosition);
                    float nx = worldPos.GetX();
                    float ny = worldPos.GetY();
                    float nz = worldPos.GetZ();
                    
                    if (!newlyAllocated && !changed) {
                        if (std::abs(javaVertices[v * 3] - nx) > 1e-4f ||
                            std::abs(javaVertices[v * 3 + 1] - ny) > 1e-4f ||
                            std::abs(javaVertices[v * 3 + 2] - nz) > 1e-4f) {
                            changed = true;
                        }
                    }
                    
                    javaVertices[v * 3] = nx;
                    javaVertices[v * 3 + 1] = ny;
                    javaVertices[v * 3 + 2] = nz;
                }

                env->ReleaseFloatArrayElements(innerArr, javaVertices, 0);
                
                if (changed) {
                    jboolean val = 1;
                    env->SetBooleanArrayRegion(isVertexDataDirtyArr, i, 1, &val);
                    vertexDirtyIndices[vertexDirtyCount++] = i;
                }
                
                env->DeleteLocalRef(innerArr);
            }
        }
    }

    // Pass 3: Finalize dirty indices list for network broadcasting.
    // Re-acquires the dirty index array to consolidate results from both passes.
    int totalDirtyCount = 0;
    {
        jint* dirtyIndices = (jint*)env->GetPrimitiveArrayCritical(dirtyIndicesOutputArr, nullptr);
        jboolean* isTransformDirty = (jboolean*)env->GetPrimitiveArrayCritical(isTransformDirtyArr, nullptr);
        
        // Collect indices marked in Pass 1.
        for (int b = 0; b < count; ++b) {
            jint i;
            env->GetIntArrayRegion(indicesArr, b, 1, &i);
            if (isTransformDirty[i]) {
                dirtyIndices[totalDirtyCount++] = i;
            }
        }
        
        // Append unique indices marked in Pass 2.
        for (int v = 0; v < vertexDirtyCount; ++v) {
            int i = vertexDirtyIndices[v];
            if (!isTransformDirty[i]) {
                dirtyIndices[totalDirtyCount++] = i;
            }
        }

        env->ReleasePrimitiveArrayCritical(isTransformDirtyArr, isTransformDirty, 0);
        env->ReleasePrimitiveArrayCritical(dirtyIndicesOutputArr, dirtyIndices, 0);
    }

    return totalDirtyCount;
}

}