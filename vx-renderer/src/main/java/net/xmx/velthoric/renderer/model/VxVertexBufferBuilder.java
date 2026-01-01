/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.renderer.model;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.xmx.velthoric.renderer.gl.VxVertexLayout;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A helper class to construct a ByteBuffer containing interleaved vertex data.
 * Used by the {@link VxModelBaker}.
 *
 * @author xI-Mx-Ix
 */
public class VxVertexBufferBuilder {

    /**
     * The size in bytes of a single vertex in the buffer.
     * Must match {@link VxVertexLayout#STRIDE} (44 bytes).
     */
    public static final int STRIDE = VxVertexLayout.STRIDE;

    private ByteBuffer buffer;

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
        // Ensure we have enough space for 44 bytes
        ensureCapacity();

        // 1. Position (0-12)
        buffer.putFloat(pos.getFloat(pIdx * 3));
        buffer.putFloat(pos.getFloat(pIdx * 3 + 1));
        buffer.putFloat(pos.getFloat(pIdx * 3 + 2));

        // 2. Color (12-16)
        float cR = (colors.size() > pIdx * 3) ? colors.getFloat(pIdx * 3) : 1.0f;
        float cG = (colors.size() > pIdx * 3 + 1) ? colors.getFloat(pIdx * 3 + 1) : 1.0f;
        float cB = (colors.size() > pIdx * 3 + 2) ? colors.getFloat(pIdx * 3 + 2) : 1.0f;

        float r = materialColor[0] * cR;
        float g = materialColor[1] * cG;
        float b = materialColor[2] * cB;

        int r_byte = (int)(r * 255.0f) & 0xFF;
        int g_byte = (int)(g * 255.0f) & 0xFF;
        int b_byte = (int)(b * 255.0f) & 0xFF;
        int a_byte = (int)(materialColor[3] * 255.0f) & 0xFF;
        buffer.putInt(a_byte << 24 | b_byte << 16 | g_byte << 8 | r_byte);

        // 3. UV0 (16-24)
        float u, v;
        if (uIdx != -1) {
            u = uv.getFloat(uIdx * 2);
            v = uv.getFloat(uIdx * 2 + 1);
        } else {
            u = 0.0f;
            v = 0.0f;
        }
        buffer.putFloat(u);
        buffer.putFloat(v);

        // 4. Lightmap UV2 (24-28)
        buffer.putShort((short) 0);
        buffer.putShort((short) 0);

        // 5. Normal (28-31)
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

        // 6. Tangent (31-35) - Placeholder, filled by TangentGenerator
        buffer.put((byte) 0);
        buffer.put((byte) 0);
        buffer.put((byte) 0);
        buffer.put((byte) 0);

        // 7. Padding (35-36)
        buffer.put((byte) 0);

        // 8. MidTexCoord (36-44) - Duplicate of UV0 for shader compatibility
        // THIS is where it crashed because capacity was checked against 36, not 44.
        buffer.putFloat(u);
        buffer.putFloat(v);
    }

    private void ensureCapacity() {
        if (buffer.remaining() < STRIDE) {
            ByteBuffer newBuffer = ByteBuffer.allocateDirect(buffer.capacity() * 2).order(ByteOrder.nativeOrder());
            buffer.flip();
            newBuffer.put(buffer);
            buffer = newBuffer;
        }
    }

    /**
     * Prepares the buffer for reading, returns it, and resets the builder for reuse.
     * @return The data.
     */
    public ByteBuffer buildAndReset() {
        buffer.flip();
        ByteBuffer result = ByteBuffer.allocateDirect(buffer.remaining()).order(ByteOrder.nativeOrder());
        result.put(buffer);
        result.flip();

        // Reset state
        buffer.clear();
        return result;
    }
}