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
 * Tangents are essential for advanced lighting techniques like normal mapping, as they
 * define the orientation of the texture space on the model's surface.
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
     * Calculates and writes tangent vectors for each vertex in the provided buffer.
     * The process involves two passes:
     * 1. Calculate a base tangent for each triangle.
     * 2. Orthogonalize the tangent against the normal for each vertex (Gram-Schmidt process).
     *
     * @param buffer The ByteBuffer containing interleaved vertex data.
     * @param vertexCount The total number of vertices in the buffer.
     */
    public static void calculate(ByteBuffer buffer, int vertexCount) {
        // First pass: Calculate per-triangle tangents
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

            Vector3f tangent = new Vector3f(1.0f, 0.0f, 0.0f); // Default tangent

            float determinant = deltaUV1.x * deltaUV2.y - deltaUV2.x * deltaUV1.y;

            // Check for degenerate UVs (zero area), which would cause a division by zero.
            if (Math.abs(determinant) > 1e-6) {
                float f = 1.0f / determinant;
                tangent.set(
                    f * (deltaUV2.y * edge1.x - deltaUV1.y * edge2.x),
                    f * (deltaUV2.y * edge1.y - deltaUV1.y * edge2.y),
                    f * (deltaUV2.y * edge1.z - deltaUV1.y * edge2.z)
                );
            }

            // If the calculation results in an invalid vector (Infinity or NaN),
            // reset to a safe default to prevent rendering artifacts.
            if (Float.isInfinite(tangent.x) || Float.isNaN(tangent.x)) {
                tangent.set(1.0f, 0.0f, 0.0f);
            }

            if (tangent.lengthSquared() > 0) {
                tangent.normalize();
            }

            writePackedSByte3(buffer, i0 + TANGENT_OFFSET, tangent);
            writePackedSByte3(buffer, i1 + TANGENT_OFFSET, tangent);
            writePackedSByte3(buffer, i2 + TANGENT_OFFSET, tangent);
        }

        // Second pass: Orthogonalize tangents for each vertex (Gram-Schmidt process)
        for (int i = 0; i < vertexCount; ++i) {
            int base = i * STRIDE;
            Vector3f n = readPackedSByte3(buffer, base + NORM_OFFSET);
            Vector3f t = readPackedSByte3(buffer, base + TANGENT_OFFSET);

            // Orthogonalize: t' = normalize(t - n * dot(t, n))
            t.sub(n.mul(n.dot(t), new Vector3f()));

            // If the tangent becomes a zero vector (if t was parallel to n),
            // generate a new, valid tangent.
            if (t.lengthSquared() < 1e-6f) {
                Vector3f up = new Vector3f(0.0f, 1.0f, 0.0f);
                // If normal is also parallel to 'up', use a different axis to avoid a zero cross product.
                if (Math.abs(n.dot(up)) > 0.99f) {
                    up.set(1.0f, 0.0f, 0.0f);
                }
                n.cross(up, t);
            }

            t.normalize();
            writePackedSByte3(buffer, base + TANGENT_OFFSET, t);
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

    private static void writePackedSByte3(ByteBuffer b, int offset, Vector3f v) {
        b.put(offset, (byte) (v.x * 127));
        b.put(offset + 1, (byte) (v.y * 127));
        b.put(offset + 2, (byte) (v.z * 127));
    }
}