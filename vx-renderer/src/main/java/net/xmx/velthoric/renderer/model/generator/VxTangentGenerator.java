/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.renderer.model.generator;

import org.joml.Vector2f;
import org.joml.Vector3f;

import java.nio.ByteBuffer;

/**
 * Utility to calculate tangent vectors for a given set of vertices in a ByteBuffer.
 * <p>
 * This implementation calculates the full 4-component tangent vector. The 4th component (W)
 * represents the handedness of the tangent basis, which is required by shaders to
 * correctly reconstruct the TBN matrix, especially when UVs are mirrored.
 *
 * @author xI-Mx-Ix
 */
public class VxTangentGenerator {
    private static final int STRIDE = 36;
    private static final int POS_OFFSET = 0;
    private static final int UV_OFFSET = 16;
    private static final int NORM_OFFSET = 28;
    private static final int TANGENT_OFFSET = 31;

    /**
     * Calculates and writes tangent vectors (xyzw) for each vertex in the provided buffer.
     * The process involves two passes:
     * 1. Calculate base tangent and bitangent directions for each triangle.
     * 2. Orthogonalize the tangent against the normal (Gram-Schmidt) and compute handedness.
     *
     * @param buffer The ByteBuffer containing interleaved vertex data.
     * @param vertexCount The total number of vertices in the buffer.
     */
    public static void calculate(ByteBuffer buffer, int vertexCount) {
        // Temporary storage for accumulating tangents and bitangents per vertex.
        // Since the buffer is flattened (non-indexed) at this stage, vertices are not shared.
        // We can process triangles directly in the buffer.

        // However, for flat buffers, we can compute per-triangle and assign to vertices immediately.
        // To support smooth shading where vertices might be duplicated with same pos/norm but different UVs,
        // we calculate based on the triangle index.

        Vector3f[] tempTangents = new Vector3f[vertexCount];
        Vector3f[] tempBitangents = new Vector3f[vertexCount];

        for (int i = 0; i < vertexCount; i++) {
            tempTangents[i] = new Vector3f(0, 0, 0);
            tempBitangents[i] = new Vector3f(0, 0, 0);
        }

        // Pass 1: Accumulate Tangents and Bitangents
        for (int i = 0; i < vertexCount; i += 3) {
            int i0 = i * STRIDE;
            int i1 = (i + 1) * STRIDE;
            int i2 = (i + 2) * STRIDE;

            Vector3f p0 = readVec3(buffer, i0 + POS_OFFSET);
            Vector3f p1 = readVec3(buffer, i1 + POS_OFFSET);
            Vector3f p2 = readVec3(buffer, i2 + POS_OFFSET);

            Vector2f uv0 = readVec2(buffer, i0 + UV_OFFSET);
            Vector2f uv1 = readVec2(buffer, i1 + UV_OFFSET);
            Vector2f uv2 = readVec2(buffer, i2 + UV_OFFSET);

            Vector3f edge1 = p1.sub(p0, new Vector3f());
            Vector3f edge2 = p2.sub(p0, new Vector3f());

            Vector2f deltaUV1 = uv1.sub(uv0, new Vector2f());
            Vector2f deltaUV2 = uv2.sub(uv0, new Vector2f());

            float r = 1.0f / (deltaUV1.x * deltaUV2.y - deltaUV2.x * deltaUV1.y);

            // Tangent (aligns with U)
            Vector3f tangent = new Vector3f(
                    (deltaUV2.y * edge1.x - deltaUV1.y * edge2.x) * r,
                    (deltaUV2.y * edge1.y - deltaUV1.y * edge2.y) * r,
                    (deltaUV2.y * edge1.z - deltaUV1.y * edge2.z) * r
            );

            // Bitangent (aligns with V)
            Vector3f bitangent = new Vector3f(
                    (deltaUV1.x * edge2.x - deltaUV2.x * edge1.x) * r,
                    (deltaUV1.x * edge2.y - deltaUV2.x * edge1.y) * r,
                    (deltaUV1.x * edge2.z - deltaUV2.x * edge1.z) * r
            );

            // Accumulate (for flat shaded meshes, this just sets the value)
            tempTangents[i].add(tangent);
            tempTangents[i+1].add(tangent);
            tempTangents[i+2].add(tangent);

            tempBitangents[i].add(bitangent);
            tempBitangents[i+1].add(bitangent);
            tempBitangents[i+2].add(bitangent);
        }

        // Pass 2: Orthogonalize and Write
        for (int i = 0; i < vertexCount; ++i) {
            int base = i * STRIDE;
            Vector3f n = readPackedSByte3(buffer, base + NORM_OFFSET);
            Vector3f t = tempTangents[i];
            Vector3f b = tempBitangents[i];

            // Gram-Schmidt orthogonalize: t = normalize(t - n * dot(n, t));
            Vector3f tOrth = new Vector3f(t).sub(new Vector3f(n).mul(n.dot(t))).normalize();

            // Handle degenerate cases where tangent is 0 or parallel to normal
            if (!Float.isFinite(tOrth.lengthSquared()) || tOrth.lengthSquared() < 1e-6) {
                tOrth.set(1, 0, 0);
                if (Math.abs(n.dot(tOrth)) > 0.9) tOrth.set(0, 1, 0);
            }

            // Calculate Handedness (W component)
            // The cross product of Normal and Tangent should align with the Bitangent.
            // If it opposes the Bitangent, the texture space is mirrored (handedness = -1).
            Vector3f nCrossT = new Vector3f(n).cross(tOrth);
            float handedness = (nCrossT.dot(b) < 0.0f) ? -1.0f : 1.0f;

            // Write 4 bytes: XYZ (Tangent) + W (Handedness)
            writePackedSByte4(buffer, base + TANGENT_OFFSET, tOrth, handedness);
        }
    }

    private static Vector3f readVec3(ByteBuffer b, int offset) {
        return new Vector3f(b.getFloat(offset), b.getFloat(offset + 4), b.getFloat(offset + 8));
    }

    private static Vector2f readVec2(ByteBuffer b, int offset) {
        return new Vector2f(b.getFloat(offset), b.getFloat(offset + 4));
    }

    private static Vector3f readPackedSByte3(ByteBuffer b, int offset) {
        return new Vector3f(b.get(offset) / 127f, b.get(offset + 1) / 127f, b.get(offset + 2) / 127f);
    }

    private static void writePackedSByte4(ByteBuffer b, int offset, Vector3f v, float w) {
        b.put(offset, (byte) (v.x * 127));
        b.put(offset + 1, (byte) (v.y * 127));
        b.put(offset + 2, (byte) (v.z * 127));
        // The W component is usually exactly 1.0 or -1.0, so 127 or -127 is sufficient.
        b.put(offset + 3, (byte) (w * 127));
    }
}