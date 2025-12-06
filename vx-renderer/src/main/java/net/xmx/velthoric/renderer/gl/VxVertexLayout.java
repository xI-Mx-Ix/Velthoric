/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.renderer.gl;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * A utility class that defines the standard vertex layout for all meshes in the system.
 * This centralizes the vertex attribute configuration to ensure consistency and
 * avoid code duplication between different mesh storage strategies (e.g., standalone vs. arena).
 *
 * @author xI-Mx-Ix
 */
public final class VxVertexLayout {

    /**
     * The size of a single vertex in bytes.
     * <ul>
     *   <li>Position (vec3): 3 * 4 = 12 bytes</li>
     *   <li>Color (4 ubytes): 4 * 1 = 4 bytes</li>
     *   <li>UV0 (vec2): 2 * 4 = 8 bytes</li>
     *   <li>UV2 (2 shorts): 2 * 2 = 4 bytes</li>
     *   <li>Normal (3 bytes): 3 * 1 = 3 bytes</li>
     *   <li>Tangent (3 bytes): 3 * 1 = 3 bytes</li>
     *   <li>Total: 36 bytes</li>
     * </ul>
     */
    public static final int STRIDE = 36;

    // Vertex attribute locations, matching the shader bindings.
    public static final int AT_POSITION = 0;
    public static final int AT_COLOR = 1;
    public static final int AT_UV0 = 2;
    public static final int AT_UV2 = 4;
    public static final int AT_NORMAL = 5;
    public static final int AT_TANGENT = 13;

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private VxVertexLayout() {}

    /**
     * Configures the vertex attribute pointers for the currently bound Vertex Array Object (VAO).
     * This method defines the memory layout for all vertices, specifying how the GPU should
     * interpret the raw data from a Vertex Buffer Object (VBO).
     */
    public static void setupVertexAttributes() {
        GL30.glEnableVertexAttribArray(AT_POSITION);
        GL30.glVertexAttribPointer(AT_POSITION, 3, GL11.GL_FLOAT, false, STRIDE, 0);

        GL30.glEnableVertexAttribArray(AT_COLOR);
        GL30.glVertexAttribPointer(AT_COLOR, 4, GL11.GL_UNSIGNED_BYTE, true, STRIDE, 12);

        GL30.glEnableVertexAttribArray(AT_UV0);
        GL30.glVertexAttribPointer(AT_UV0, 2, GL11.GL_FLOAT, false, STRIDE, 16);

        GL30.glEnableVertexAttribArray(AT_UV2);
        GL30.glVertexAttribIPointer(AT_UV2, 2, GL11.GL_SHORT, STRIDE, 24);

        GL30.glEnableVertexAttribArray(AT_NORMAL);
        GL30.glVertexAttribPointer(AT_NORMAL, 3, GL11.GL_BYTE, true, STRIDE, 28);

        GL30.glEnableVertexAttribArray(AT_TANGENT);
        GL30.glVertexAttribPointer(AT_TANGENT, 3, GL11.GL_BYTE, true, STRIDE, 31);
    }
}