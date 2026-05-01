/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.jni;

/**
 * JNI bridge for batch-processing physics state synchronization.
 * Designed for Structure of Arrays (SoA) data layouts to minimize native overhead.
 *
 * @author xI-Mx-Ix
 */
public class BatchPhysicsSync {

    /**
     * Synchronizes a batch of physics bodies from native memory to Java buffers.
     *
     * @param physicsSystemPtr     The native memory address (pointer) of the Jolt {@code PhysicsSystem}.
     * @param count                The number of bodies to process in this batch.
     * @param indices              The global indices within the {@code VxServerBodyDataContainer} for each body.
     * @param bodyIds              The native Jolt {@code BodyID}s to be queried.
     * @param behaviorBits         Bitmasks for each body, used by native code to filter logic execution.
     * @param posX                 Output array for X-coordinates (Double-precision for large-world support).
     * @param posY                 Output array for Y-coordinates.
     * @param posZ                 Output array for Z-coordinates.
     * @param rotX                 Output array for the X-component of the rotation quaternions.
     * @param rotY                 Output array for the Y-component of the rotation quaternions.
     * @param rotZ                 Output array for the Z-component of the rotation quaternions.
     * @param rotW                 Output array for the W-component of the rotation quaternions.
     * @param velX                 Output array for linear velocity X.
     * @param velY                 Output array for linear velocity Y.
     * @param velZ                 Output array for linear velocity Z.
     * @param angVelX              Output array for angular velocity X.
     * @param angVelY              Output array for angular velocity Y.
     * @param angVelZ              Output array for angular velocity Z.
     * @param aabbMinX             Output array for the minimum AABB X-boundary.
     * @param aabbMinY             Output array for the minimum AABB Y-boundary.
     * @param aabbMinZ             Output array for the minimum AABB Z-boundary.
     * @param aabbMaxX             Output array for the maximum AABB X-boundary.
     * @param aabbMaxY             Output array for the maximum AABB Y-boundary.
     * @param aabbMaxZ             Output array for the maximum AABB Z-boundary.
     * @param isActive             Input/Output array indicating if a body is active (awake).
     * @param isTransformDirty     Output flags indicating if the transform (pos/rot) has changed.
     * @param isVertexDataDirty    Output flags indicating if soft-body vertex data has changed.
     * @param lastUpdateTimestamp  Array to store the simulation timestamp for each body's last sync.
     * @param motionTypeOutput     Output buffer for the {@code EMotionType} ordinals (Static, Kinematic, Dynamic).
     * @param dirtyIndicesOutput   Buffer to be filled with the {@code indices} of bodies requiring a network sync.
     * @param vertexData           2D-array containing vertex position arrays for soft-body mesh synchronization.
     * @param softBodyBehaviorMask The behavior bitmask used to identify bodies that require vertex processing.
     * @param timestampNanos       The current simulation time in nanoseconds.
     * @return The total number of indices written to {@code dirtyIndicesOutput}.
     */
    public static native int syncPhysicsNative(
            long physicsSystemPtr,
            int count,
            int[] indices,
            int[] bodyIds,
            long[] behaviorBits,
            double[] posX, double[] posY, double[] posZ,
            float[] rotX, float[] rotY, float[] rotZ, float[] rotW,
            float[] velX, float[] velY, float[] velZ,
            float[] angVelX, float[] angVelY, float[] angVelZ,
            float[] aabbMinX, float[] aabbMinY, float[] aabbMinZ,
            float[] aabbMaxX, float[] aabbMaxY, float[] aabbMaxZ,
            boolean[] isActive,
            boolean[] isTransformDirty,
            boolean[] isVertexDataDirty,
            long[] lastUpdateTimestamp,
            byte[] motionTypeOutput,
            int[] dirtyIndicesOutput,
            float[][] vertexData,
            long softBodyBehaviorMask,
            long timestampNanos
    );
}