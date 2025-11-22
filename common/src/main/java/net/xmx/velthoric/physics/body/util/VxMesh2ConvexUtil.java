/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.util;

import com.github.stephengold.joltjni.ConvexHullShapeSettings;
import com.github.stephengold.joltjni.Jolt;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility class for converting raw render mesh data into Jolt Physics convex hull shapes.
 * <p>
 * This class includes an optimization algorithm that downsamples the vertex data.
 * Instead of passing every single vertex from a high-detail model to the physics engine,
 * it uses a voxel grid approach to select representative points. This maintains the
 * accurate volume of the object while significantly reducing the complexity of the
 * physics hull generation.
 *
 * @author xI-Mx-Ix
 */
public final class VxMesh2ConvexUtil {

    /**
     * The default resolution for the point reduction grid in meters.
     * Points falling within the same 2cm cube will be merged into a single point.
     * Increasing this value reduces physics precision but improves performance.
     */
    private static final float DEFAULT_TOLERANCE = 0.02f;

    private VxMesh2ConvexUtil() {
        // Prevent instantiation
    }

    /**
     * Creates convex hull settings from a raw FloatBuffer containing tightly packed positions (XYZ).
     *
     * @param vertexData The buffer containing vertex positions (limit must be count * 3).
     * @return The Jolt shape settings.
     */
    public static ConvexHullShapeSettings createHull(FloatBuffer vertexData) {
        return createHull(vertexData, DEFAULT_TOLERANCE);
    }

    /**
     * Creates convex hull settings from a raw FloatBuffer with a custom tolerance.
     *
     * @param vertexData The buffer containing vertex positions (XYZ).
     * @param tolerance  The grid cell size for downsampling.
     * @return The Jolt shape settings.
     */
    public static ConvexHullShapeSettings createHull(FloatBuffer vertexData, float tolerance) {
        // Convert FloatBuffer to ByteBuffer view for the generic method, stride is 12 bytes (3 * 4)
        ByteBuffer byteView = ByteBuffer.allocateDirect(vertexData.remaining() * 4).order(ByteOrder.nativeOrder());
        FloatBuffer copy = byteView.asFloatBuffer();
        copy.put(vertexData); // Copy to ensure we have a byte accessible buffer
        // Reset position for reading
        vertexData.rewind();

        return createHull(byteView, 0, vertexData.limit() / 3, 12, tolerance);
    }

    /**
     * Creates convex hull settings from a generic ByteBuffer.
     * This is useful for interleaved vertex data (e.g., Position, Normal, UV in one buffer).
     * <p>
     * It assumes the Position (XYZ) is located at the beginning of the stride.
     *
     * @param vertexData  The raw byte buffer containing vertex data.
     * @param startOffset The byte offset in the buffer where the first vertex starts.
     * @param vertexCount The number of vertices to process.
     * @param stride      The size of a single vertex in bytes (e.g., 32 for Pos+Norm+UV).
     * @return The Jolt shape settings.
     */
    public static ConvexHullShapeSettings createHull(ByteBuffer vertexData, int startOffset, int vertexCount, int stride) {
        return createHull(vertexData, startOffset, vertexCount, stride, DEFAULT_TOLERANCE);
    }

    /**
     * Creates convex hull settings from a generic ByteBuffer with custom tolerance.
     *
     * @param vertexData  The raw byte buffer containing vertex data.
     * @param startOffset The byte offset in the buffer where the first vertex starts.
     * @param vertexCount The number of vertices to process.
     * @param stride      The size of a single vertex in bytes.
     * @param tolerance   The grid cell size for downsampling.
     * @return The Jolt shape settings, or null if no points remain after filtering.
     */
    public static ConvexHullShapeSettings createHull(ByteBuffer vertexData, int startOffset, int vertexCount, int stride, float tolerance) {
        // Ensure the buffer is in the correct order for reading floats
        vertexData.order(ByteOrder.nativeOrder());

        // A set to track unique grid cells (spatial hashing)
        Set<Long> visitedVoxels = new HashSet<>();

        // Temporary list to hold the filtered points (x, y, z)
        List<Float> filteredPoints = new ArrayList<>(Math.min(vertexCount * 3, 1024));

        for (int i = 0; i < vertexCount; i++) {
            // Calculate absolute byte offset for the current vertex
            int currentOffset = startOffset + (i * stride);

            // Read Position (Assuming XYZ are the first 12 bytes at the current offset)
            float x = vertexData.getFloat(currentOffset);
            float y = vertexData.getFloat(currentOffset + 4);
            float z = vertexData.getFloat(currentOffset + 8);

            // Voxel Grid Approximation
            // Quantize the position to integer coordinates based on tolerance.
            // This effectively snaps vertices to a 3D grid.
            long gx = (long) Math.floor(x / tolerance);
            long gy = (long) Math.floor(y / tolerance);
            long gz = (long) Math.floor(z / tolerance);

            // Create a unique hash for this voxel
            long voxelHash = hashVoxel(gx, gy, gz);

            // Only add the point if this voxel hasn't been visited yet.
            if (visitedVoxels.add(voxelHash)) {
                filteredPoints.add(x);
                filteredPoints.add(y);
                filteredPoints.add(z);
            }
        }

        if (filteredPoints.isEmpty()) {
            return null;
        }

        // Convert to Jolt-compatible Direct FloatBuffer
        int numPoints = filteredPoints.size() / 3;
        FloatBuffer joltBuffer = Jolt.newDirectFloatBuffer(filteredPoints.size());

        for (float f : filteredPoints) {
            joltBuffer.put(f);
        }
        joltBuffer.flip();

        // Create the Jolt Settings
        return new ConvexHullShapeSettings(numPoints, joltBuffer);
    }

    /**
     * Generates a spatial hash for a 3D integer coordinate.
     *
     * @param x Grid X
     * @param y Grid Y
     * @param z Grid Z
     * @return A unique long hash.
     */
    private static long hashVoxel(long x, long y, long z) {
        // A simple XOR-shift style hash for spatial coordinates
        long h = x * 73856093;
        h = h ^ (y * 19349663);
        h = h ^ (z * 83492791);
        return h;
    }
}