/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.renderer.gl;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

/**
 * A utility class for safely managing and isolating critical OpenGL state.
 * <p>
 * This class provides a mechanism to save the current bindings for Vertex Array Objects (VAO),
 * Vertex Buffer Objects (VBO), and Element Buffer Objects (EBO) before custom rendering operations
 * and restore them afterward to prevent conflicts with the vanilla renderer.
 *
 * @author xI-Mx-Ix
 */
public class VxGlState {
    private static int previousVaoId = -1;
    private static int previousVboId = -1;
    private static int previousEboId = -1;

    /**
     * Queries and stores the currently bound VAO, VBO, and EBO.
     */
    public static void saveCurrentState() {
        previousVaoId = GL30.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        previousVboId = GL15.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        previousEboId = GL15.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
    }

    /**
     * Restores the VAO, VBO, and EBO that were saved by the last call to {@link #saveCurrentState()}.
     */
    public static void restorePreviousState() {
        if (previousVaoId != -1) {
            GL30.glBindVertexArray(previousVaoId);
            previousVaoId = -1;
        }
        if (previousVboId != -1) {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousVboId);
            previousVboId = -1;
        }
        if (previousEboId != -1) {
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, previousEboId);
            previousEboId = -1;
        }
    }
}