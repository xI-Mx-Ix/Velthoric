/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.renderer.model.raw;

import it.unimi.dsi.fastutil.ints.IntArrayList;

/**
 * Stores the raw face indices for a specific material group using a flat integer array.
 * <p>
 * Instead of allocating an object per face (which causes massive GC pressure),
 * this class packs the 9 indices required for a triangle (3 vertices * (Position + UV + Normal))
 * into a contiguous {@link IntArrayList}.
 * <p>
 * <b>Data Layout per Face (9 integers):</b><br>
 * [v0, v1, v2, uv0, uv1, uv2, n0, n1, n2]
 *
 * @author xI-Mx-Ix
 */
public class VxRawMesh {

    /**
     * The stride size for a single face in the index array.
     * 3 vertices * 3 index types (Pos, UV, Norm) = 9 integers.
     */
    public static final int FACE_STRIDE = 9;

    /**
     * The flat list of indices.
     */
    public final IntArrayList indices = new IntArrayList();

    /**
     * Adds a single face (triangle) to this mesh.
     *
     * @param v0 Index of position vertex 0.
     * @param v1 Index of position vertex 1.
     * @param v2 Index of position vertex 2.
     * @param uv0 Index of texture coord 0 (-1 if none).
     * @param uv1 Index of texture coord 1 (-1 if none).
     * @param uv2 Index of texture coord 2 (-1 if none).
     * @param n0 Index of normal 0 (-1 if none).
     * @param n1 Index of normal 1 (-1 if none).
     * @param n2 Index of normal 2 (-1 if none).
     */
    public void addFace(int v0, int v1, int v2, int uv0, int uv1, int uv2, int n0, int n1, int n2) {
        // Positions
        indices.add(v0);
        indices.add(v1);
        indices.add(v2);
        // UVs
        indices.add(uv0);
        indices.add(uv1);
        indices.add(uv2);
        // Normals
        indices.add(n0);
        indices.add(n1);
        indices.add(n2);
    }

    /**
     * Gets the number of faces (triangles) in this mesh.
     *
     * @return The face count.
     */
    public int getFaceCount() {
        return indices.size() / FACE_STRIDE;
    }

    /**
     * Checks if a specific face has normal indices defined.
     *
     * @param faceIndex The index of the face (0 to count-1).
     * @return True if normals are present.
     */
    public boolean hasNormals(int faceIndex) {
        int base = faceIndex * FACE_STRIDE;
        // Check normal indices (offsets 6, 7, 8 in the stride)
        return indices.getInt(base + 6) != -1;
    }

    // --- Accessor Methods for Logic/Editing ---

    /**
     * Retrieves the position index for a specific vertex of a face.
     *
     * @param faceIndex The face index.
     * @param vertexIndex The vertex index within the face (0, 1, or 2).
     * @return The global position index.
     */
    public int getPositionIndex(int faceIndex, int vertexIndex) {
        return indices.getInt((faceIndex * FACE_STRIDE) + vertexIndex);
    }

    public int getUvIndex(int faceIndex, int vertexIndex) {
        return indices.getInt((faceIndex * FACE_STRIDE) + 3 + vertexIndex);
    }

    public int getNormalIndex(int faceIndex, int vertexIndex) {
        return indices.getInt((faceIndex * FACE_STRIDE) + 6 + vertexIndex);
    }
}