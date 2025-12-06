/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.renderer.mesh.impl;

import net.xmx.velthoric.renderer.gl.VxDrawCommand;
import net.xmx.velthoric.renderer.gl.VxVertexBuffer;
import net.xmx.velthoric.renderer.mesh.VxAbstractRenderableMesh;
import net.xmx.velthoric.renderer.mesh.VxMeshDefinition;

import java.nio.ByteBuffer;

/**
 * A mesh that possesses its own dedicated Vertex Buffer Object (VBO).
 * <p>
 * This class replaces the old StandaloneMesh and utilizes the new {@link VxVertexBuffer} wrapper.
 * It is useful for large or frequently changing meshes that shouldn't share
 * the arena buffer, or when strict isolation is required.
 *
 * @author xI-Mx-Ix
 */
public class VxDedicatedMesh extends VxAbstractRenderableMesh {

    /**
     * The dedicated vertex buffer instance containing the mesh data.
     */
    private final VxVertexBuffer vertexBuffer;

    /**
     * Constructs a dedicated mesh from a generic mesh definition.
     * Automatically uploads vertex data into a new VBO.
     *
     * @param definition The mesh definition containing data and structure.
     */
    public VxDedicatedMesh(VxMeshDefinition definition) {
        super(definition.allDrawCommands, definition.getGroupDrawCommands());

        ByteBuffer data = definition.getVertexData();
        // Create a dedicated buffer for this mesh with STATIC_DRAW usage (false for dynamic).
        this.vertexBuffer = new VxVertexBuffer(data.remaining(), false);
        this.vertexBuffer.uploadSubData(0, data);

        initializeTextures();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Binds the unique VBO/VAO associated with this mesh via the wrapper.
     */
    @Override
    public void setupVaoState() {
        this.vertexBuffer.bind();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Since this mesh owns the buffer starting at index 0, the final offset is just the command's local offset.
     */
    @Override
    public int getFinalVertexOffset(VxDrawCommand command) {
        return command.vertexOffset;
    }

    /**
     * Deletes the dedicated GPU resources associated with this mesh.
     */
    @Override
    public void delete() {
        if (!isDeleted) {
            this.vertexBuffer.delete();
            isDeleted = true;
        }
    }
}