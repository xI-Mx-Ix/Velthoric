/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 * Author: xI-Mx-Ix
 */
#pragma once

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

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
 * @param thiz Reference to the Java VxPhysicsSyncBehavior instance.
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
JNIEXPORT jint JNICALL Java_net_xmx_velthoric_jni_BatchPhysicsSync_syncPhysicsNative(
    JNIEnv* env, jobject thiz,
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
);

#ifdef __cplusplus
}
#endif