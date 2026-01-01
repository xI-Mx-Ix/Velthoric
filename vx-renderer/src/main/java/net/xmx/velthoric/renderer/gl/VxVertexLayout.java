/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.renderer.gl;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * A utility class that defines the standard vertex layout for all meshes in the system.
 * <p>
 * This layout includes positions, colors, texture coordinates, lightmap coordinates,
 * normals, tangents, and the <b>mid-texture coordinates</b> required by shaderpacks.
 *
 * @author xI-Mx-Ix
 */
public final class VxVertexLayout {

    /**
     * The size of a single vertex in bytes.
     * <p>
     * <b>Memory Layout:</b>
     * <ul>
     *   <li>00-12: Position (vec3 float) - 12 bytes</li>
     *   <li>12-16: Color (vec4 ubyte, normalized) - 4 bytes</li>
     *   <li>16-24: UV0 (vec2 float) - 8 bytes</li>
     *   <li>24-28: UV2 (vec2 short, unnormalized) - 4 bytes</li>
     *   <li>28-31: Normal (vec3 byte, normalized) - 3 bytes</li>
     *   <li>31-35: Tangent (vec4 byte, normalized) - 4 bytes</li>
     *   <li>35-36: Padding - 1 byte</li>
     *   <li>36-44: MidTexCoord (vec2 float) - 8 bytes</li>
     * </ul>
     * Total: 44 bytes.
     */
    public static final int STRIDE = 44;

    /**
     * The attribute location for vertex positions (vec3).
     * <p>
     * <b>Location:</b> 0
     */
    public static final int AT_POSITION = 0;

    /**
     * The attribute location for vertex colors (vec4).
     * <p>
     * <b>Location:</b> 1
     */
    public static final int AT_COLOR = 1;

    /**
     * The attribute location for primary texture coordinates (UV0).
     * <p>
     * <b>Location:</b> 2
     */
    public static final int AT_UV0 = 2;

    /**
     * The attribute location for lightmap and overlay coordinates (UV2).
     * <p>
     * <b>Location:</b> 4
     */
    public static final int AT_UV2 = 4;

    /**
     * The attribute location for vertex normals.
     * <p>
     * <b>Location:</b> 5
     */
    public static final int AT_NORMAL = 5;

    /**
     * The attribute location for the "middle" texture coordinate (mc_midTexCoord).
     * Used by shaderpacks for parallax occlusion mapping and centering.
     * <p>
     * <b>Location:</b> 7 (Iris Standard)
     */
    public static final int AT_MID_TEX = 7;

    /**
     * The attribute location for the tangent vector (at_tangent).
     * Essential for PBR Normal Mapping.
     * <p>
     * <b>Location:</b> 8 (Iris Standard)
     */
    public static final int AT_TANGENT = 8;

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private VxVertexLayout() {}

    /**
     * Configures the vertex attribute pointers for the currently bound Vertex Array Object (VAO).
     * <p>
     * This defines how the GPU interprets the byte stream from the VBO. It enables
     * the necessary vertex arrays and sets up pointers with the correct offsets and types.
     */
    public static void setupVertexAttributes() {
        // 1. Position: 3 floats (x, y, z)
        GL30.glEnableVertexAttribArray(AT_POSITION);
        GL30.glVertexAttribPointer(AT_POSITION, 3, GL11.GL_FLOAT, false, STRIDE, 0);

        // 2. Color: 4 unsigned bytes (r, g, b, a), normalized to [0, 1]
        GL30.glEnableVertexAttribArray(AT_COLOR);
        GL30.glVertexAttribPointer(AT_COLOR, 4, GL11.GL_UNSIGNED_BYTE, true, STRIDE, 12);

        // 3. Texture Coordinates (UV0): 2 floats (u, v)
        GL30.glEnableVertexAttribArray(AT_UV0);
        GL30.glVertexAttribPointer(AT_UV0, 2, GL11.GL_FLOAT, false, STRIDE, 16);

        // 4. Lightmap Coordinates (UV2): 2 shorts, kept as integers
        GL30.glEnableVertexAttribArray(AT_UV2);
        GL30.glVertexAttribIPointer(AT_UV2, 2, GL11.GL_SHORT, STRIDE, 24);

        // 5. Normal: 3 signed bytes, normalized to [-1, 1]
        GL30.glEnableVertexAttribArray(AT_NORMAL);
        GL30.glVertexAttribPointer(AT_NORMAL, 3, GL11.GL_BYTE, true, STRIDE, 28);

        // 6. Tangent: 4 signed bytes (xyz + handedness), normalized to [-1, 1]
        GL30.glEnableVertexAttribArray(AT_TANGENT);
        GL30.glVertexAttribPointer(AT_TANGENT, 4, GL11.GL_BYTE, true, STRIDE, 31);

        // 7. MidTexCoord: 2 floats (u, v) - usually a copy of UV0
        GL30.glEnableVertexAttribArray(AT_MID_TEX);
        GL30.glVertexAttribPointer(AT_MID_TEX, 2, GL11.GL_FLOAT, false, STRIDE, 36);
    }
}