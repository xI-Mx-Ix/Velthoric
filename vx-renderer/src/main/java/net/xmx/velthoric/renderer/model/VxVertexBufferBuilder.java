/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.renderer.model;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A helper class to construct a ByteBuffer containing interleaved vertex data for a single mesh group.
 * It handles dynamic resizing of the underlying buffer as more vertices are added.
 *
 * @author xI-Mx-Ix
 */
public class VxVertexBufferBuilder {

    /**
     * The size in bytes of a single vertex in the buffer.
     * (pos: 3*4, color: 1*4, uv0: 2*4, uv2: 2*2, normal: 3*1, tangent: 3*1, padding: 2*1) = 36 bytes.
     */
    public static final int STRIDE = 36;

    private ByteBuffer buffer;
    private int vertexCount = 0;

    /**
     * Constructs a new VertexBufferBuilder with an initial capacity.
     */
    public VxVertexBufferBuilder() {
        this.buffer = ByteBuffer.allocateDirect(STRIDE * 256).order(ByteOrder.nativeOrder());
    }

    /**
     * Writes a single vertex's data into the buffer.
     *
     * @param pos The list of all vertex positions.
     * @param uv The list of all texture coordinates.
     * @param norm The list of all vertex normals.
     * @param colors The list of all vertex colors.
     * @param pIdx The index for the position of this vertex.
     * @param uIdx The index for the texture coordinate of this vertex.
     * @param nIdx The index for the normal of this vertex.
     * @param materialColor The base color of the material.
     * @param calculatedNormal A pre-calculated face normal to use if no vertex normal is available.
     */
    public void writeVertex(FloatArrayList pos, FloatArrayList uv, FloatArrayList norm, FloatArrayList colors,
                            int pIdx, int uIdx, int nIdx, float[] materialColor, Vector3f calculatedNormal) {
        ensureCapacity();

        // Position (3 floats)
        buffer.putFloat(pos.getFloat(pIdx * 3));
        buffer.putFloat(pos.getFloat(pIdx * 3 + 1));
        buffer.putFloat(pos.getFloat(pIdx * 3 + 2));

        // Color (4 bytes) - modulated by material and vertex color
        float r = materialColor[0] * colors.getFloat(pIdx * 3);
        float g = materialColor[1] * colors.getFloat(pIdx * 3 + 1);
        float b = materialColor[2] * colors.getFloat(pIdx * 3 + 2);
        int r_byte = (int)(r * 255.0f) & 0xFF;
        int g_byte = (int)(g * 255.0f) & 0xFF;
        int b_byte = (int)(b * 255.0f) & 0xFF;
        int a_byte = (int)(materialColor[3] * 255.0f) & 0xFF;
        buffer.putInt(a_byte << 24 | b_byte << 16 | g_byte << 8 | r_byte);

        // Texture Coordinates (2 floats)
        if (uIdx != -1) {
            buffer.putFloat(uv.getFloat(uIdx * 2));
            buffer.putFloat(uv.getFloat(uIdx * 2 + 1));
        } else {
            buffer.putFloat(0.0f);
            buffer.putFloat(0.0f);
        }

        // Lightmap UV (2 shorts) - initialized to 0, set at render time
        buffer.putShort((short) 0);
        buffer.putShort((short) 0);

        // Normal (3 bytes)
        if (nIdx != -1) {
            buffer.put((byte) (norm.getFloat(nIdx * 3) * 127));
            buffer.put((byte) (norm.getFloat(nIdx * 3 + 1) * 127));
            buffer.put((byte) (norm.getFloat(nIdx * 3 + 2) * 127));
        } else if (calculatedNormal != null) {
            buffer.put((byte) (calculatedNormal.x * 127));
            buffer.put((byte) (calculatedNormal.y * 127));
            buffer.put((byte) (calculatedNormal.z * 127));
        } else {
            buffer.put((byte) 0);
            buffer.put((byte) 127);
            buffer.put((byte) 0);
        }

        // Tangent (3 bytes) + Padding (2 bytes) - initialized to 0, calculated later
        buffer.put((byte) 0);
        buffer.put((byte) 0);
        buffer.put((byte) 0);
        buffer.putShort((short)0);

        vertexCount++;
    }

    /**
     * Ensures the buffer has enough remaining capacity for at least one more vertex.
     * If not, a new, larger buffer is allocated and the old data is copied over.
     */
    private void ensureCapacity() {
        if (buffer.remaining() < STRIDE) {
            ByteBuffer newBuffer = ByteBuffer.allocateDirect(buffer.capacity() * 2).order(ByteOrder.nativeOrder());
            buffer.flip();
            newBuffer.put(buffer);
            buffer = newBuffer;
        }
    }

    /**
     * @return The number of vertices that have been written to this builder.
     */
    public int getVertexCount() {
        return vertexCount;
    }

    /**
     * @return True if no vertices have been added, false otherwise.
     */
    public boolean isEmpty() {
        return vertexCount == 0;
    }

    /**
     * Finalizes the buffer by flipping it, making it ready for reading.
     * @return The completed ByteBuffer containing all vertex data.
     */
    public ByteBuffer build() {
        buffer.flip();
        return buffer;
    }
}