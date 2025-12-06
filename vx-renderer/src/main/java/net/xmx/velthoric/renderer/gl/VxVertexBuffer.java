/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.renderer.gl;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;

import java.nio.ByteBuffer;

/**
 * Wraps an OpenGL Vertex Buffer Object (VBO) and its associated Vertex Array Object (VAO).
 * <p>
 * This class abstracts the low-level GL calls required to allocate memory on the GPU,
 * configure vertex attributes via {@link VxVertexLayout}, and upload data.
 *
 * @author xI-Mx-Ix
 */
public class VxVertexBuffer {

    private int vaoId;
    private int vboId;
    private final boolean dynamic;
    private long capacityBytes;

    /**
     * Creates a new Vertex Buffer.
     *
     * @param capacityBytes The initial capacity in bytes.
     * @param dynamic       If true, uses GL_DYNAMIC_DRAW, otherwise GL_STATIC_DRAW.
     */
    public VxVertexBuffer(long capacityBytes, boolean dynamic) {
        RenderSystem.assertOnRenderThread();
        this.capacityBytes = capacityBytes;
        this.dynamic = dynamic;
        initialize();
    }

    /**
     * Initializes the GL resources.
     */
    private void initialize() {
        this.vboId = GL30.glGenBuffers();
        this.vaoId = GL30.glGenVertexArrays();

        bind();
        // Allocate memory
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, capacityBytes, dynamic ? GL15.GL_DYNAMIC_DRAW : GL15.GL_STATIC_DRAW);
        // Setup attributes
        VxVertexLayout.setupVertexAttributes();
        unbind();
    }

    /**
     * Binds the VAO (and effectively the VBO linked to it).
     */
    public void bind() {
        GL30.glBindVertexArray(vaoId);
        // Explicitly binding the VBO is often redundant if the VAO captured it,
        // but ensures safety if external code modified the ARRAY_BUFFER binding while this VAO was active.
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
    }

    /**
     * Unbinds the VAO and VBO.
     */
    public void unbind() {
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    /**
     * Uploads data to the buffer at a specific offset.
     *
     * @param offsetBytes The offset in the buffer to start writing.
     * @param data        The data to upload.
     */
    public void uploadSubData(long offsetBytes, ByteBuffer data) {
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, offsetBytes, data);
    }

    /**
     * Copies data from this buffer to another buffer on the GPU.
     * Used for resizing operations.
     *
     * @param target     The destination buffer.
     * @param sizeBytes  The amount of bytes to copy.
     */
    public void copyTo(VxVertexBuffer target, long sizeBytes) {
        GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, this.vboId);
        GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, target.vboId);
        GL31.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER, GL31.GL_COPY_WRITE_BUFFER, 0, 0, sizeBytes);
        GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, 0);
        GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, 0);
    }

    /**
     * Deletes the GL resources.
     */
    public void delete() {
        RenderSystem.assertOnRenderThread();
        if (vboId != 0) GL15.glDeleteBuffers(vboId);
        if (vaoId != 0) GL30.glDeleteVertexArrays(vaoId);
        vboId = 0;
        vaoId = 0;
    }

    public long getCapacityBytes() {
        return capacityBytes;
    }
}