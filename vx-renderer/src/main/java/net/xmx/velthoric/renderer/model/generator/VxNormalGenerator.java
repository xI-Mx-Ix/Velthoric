/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.renderer.model.generator;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import org.joml.Vector3f;

/**
 * Utility class for calculating vertex normals for 3D models.
 *
 * @author xI-Mx-Ix
 */
public final class VxNormalGenerator {

    /**
     * Private constructor to prevent instantiation.
     */
    private VxNormalGenerator() {}

    /**
     * Calculates the normal vector of a face defined by three vertices.
     * The normal is computed using the cross product of two edges of the face.
     * This is essential for achieving correct lighting on models that do not have pre-computed normals.
     *
     * @param positions A list of vertex positions (x, y, z).
     * @param idx0 The index of the first vertex of the face.
     * @param idx1 The index of the second vertex of the face.
     * @param idx2 The index of the third vertex of the face.
     * @return A normalized {@link Vector3f} representing the face normal. Returns a default up-vector if the face is degenerate.
     */
    public static Vector3f calculateFaceNormal(FloatArrayList positions, int idx0, int idx1, int idx2) {
        Vector3f p0 = new Vector3f(
            positions.getFloat(idx0 * 3),
            positions.getFloat(idx0 * 3 + 1),
            positions.getFloat(idx0 * 3 + 2)
        );
        Vector3f p1 = new Vector3f(
            positions.getFloat(idx1 * 3),
            positions.getFloat(idx1 * 3 + 1),
            positions.getFloat(idx1 * 3 + 2)
        );
        Vector3f p2 = new Vector3f(
            positions.getFloat(idx2 * 3),
            positions.getFloat(idx2 * 3 + 1),
            positions.getFloat(idx2 * 3 + 2)
        );

        Vector3f edge1 = p1.sub(p0, new Vector3f());
        Vector3f edge2 = p2.sub(p0, new Vector3f());
        Vector3f normal = edge1.cross(edge2, new Vector3f());

        if (normal.lengthSquared() > 1e-6f) {
            normal.normalize();
        } else {
            // The face is degenerate (vertices are collinear), provide a fallback normal.
            normal.set(0.0f, 1.0f, 0.0f);
        }

        return normal;
    }
}