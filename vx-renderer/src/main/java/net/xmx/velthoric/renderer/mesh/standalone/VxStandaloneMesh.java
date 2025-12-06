/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.renderer.mesh.standalone;

import com.mojang.blaze3d.systems.RenderSystem;
import net.xmx.velthoric.renderer.VxDrawCommand;
import net.xmx.velthoric.renderer.mesh.VxAbstractRenderableMesh;
import net.xmx.velthoric.renderer.mesh.VxMeshDefinition;
import net.xmx.velthoric.renderer.mesh.VxVertexLayout;
import org.lwjgl.opengl.GL30;

/**
 * Represents a self-contained, standalone mesh on the GPU.
 * It manages its own dedicated Vertex Array Object (VAO) and Vertex Buffer Object (VBO).
 * <p>
 * Ideal for complex models like vehicles that are rendered individually.
 *
 * @author xI-Mx-Ix
 */
public class VxStandaloneMesh extends VxAbstractRenderableMesh {

    /**
     * The unique OpenGL handle for the Vertex Array Object (VAO) of this mesh.
     */
    private final int vaoId;

    /**
     * The unique OpenGL handle for the Vertex Buffer Object (VBO) of this mesh.
     */
    private final int vboId;

    /**
     * Constructs a new standalone mesh from a generic mesh definition.
     * Automatically uploads vertex data and configures grouping logic from the definition.
     *
     * @param definition The CPU-side description of the mesh.
     */
    public VxStandaloneMesh(VxMeshDefinition definition) {
        // Pass both the full command list and the grouping map to the superclass.
        // This enables the queueRenderGroup() functionality for this standalone mesh.
        super(definition.allDrawCommands, definition.getGroupDrawCommands());

        RenderSystem.assertOnRenderThread();
        this.vboId = GL30.glGenBuffers();
        this.vaoId = GL30.glGenVertexArrays();

        upload(definition);
        initializeTextures();
    }

    /**
     * Uploads the vertex data to the VBO and configures the VAO's vertex attributes.
     *
     * @param definition The mesh definition containing the vertex data.
     */
    private void upload(VxMeshDefinition definition) {
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, this.vboId);
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, definition.getVertexData(), GL30.GL_STATIC_DRAW);

        GL30.glBindVertexArray(this.vaoId);
        VxVertexLayout.setupVertexAttributes();

        // Unbind the VBO from the global target, but leave the VAO configured.
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, 0);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Binds the unique VAO associated with this standalone mesh.
     */
    @Override
    public void setupVaoState() {
        GL30.glBindVertexArray(this.vaoId);
    }

    /**
     * {@inheritDoc}
     * <p>
     * For a standalone mesh, the final offset is simply the offset within the command,
     * as the buffer starts at index 0.
     */
    @Override
    public int getFinalVertexOffset(VxDrawCommand command) {
        return command.vertexOffset;
    }

    /**
     * Deletes the GPU resources (VBO and VAO) associated with this mesh.
     */
    @Override
    public void delete() {
        if (isDeleted) return;
        RenderSystem.assertOnRenderThread();
        GL30.glDeleteBuffers(this.vboId);
        GL30.glDeleteVertexArrays(this.vaoId);
        isDeleted = true;
    }
}