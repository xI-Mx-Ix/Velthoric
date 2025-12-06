/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.renderer.mesh.arena;

import net.xmx.velthoric.renderer.VxDrawCommand;
import net.xmx.velthoric.renderer.mesh.VxAbstractRenderableMesh;
import net.xmx.velthoric.renderer.mesh.VxVertexLayout;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a lightweight handle to a mesh stored within a larger {@link VxArenaBuffer}.
 * This class does not own any GPU resources itself. It contains the offset and size information
 * required for rendering and for deallocation within the parent buffer.
 * <p>
 * Even though it is a sub-allocation, it fully supports the hierarchical grouping system
 * via {@link #queueRenderGroup(com.mojang.blaze3d.vertex.PoseStack, int, String)}.
 *
 * @author xI-Mx-Ix
 */
public class VxArenaMesh extends VxAbstractRenderableMesh {

    /**
     * A reference to the parent buffer that owns the actual GPU memory.
     */
    private final VxArenaBuffer parentBuffer;

    /**
     * The byte offset of this sub-mesh's data within the parent VBO.
     */
    private final long offsetBytes;

    /**
     * The total size in bytes of this sub-mesh's vertex data.
     */
    private final long sizeBytes;

    /**
     * The starting vertex index, calculated from the byte offset.
     * This is added to draw commands to find the correct location in the large buffer.
     */
    private final int baseVertex;

    /**
     * Constructs a new ArenaSubMesh.
     *
     * @param parentBuffer      The arena buffer that owns the vertex data.
     * @param offsetBytes       The starting byte offset of this mesh's data in the VBO.
     * @param sizeBytes         The size in bytes of this mesh's data.
     * @param allDrawCommands   The list of draw commands for the entire mesh.
     * @param groupDrawCommands The map of group-specific draw commands for animation support.
     */
    public VxArenaMesh(VxArenaBuffer parentBuffer, long offsetBytes, long sizeBytes,
                       List<VxDrawCommand> allDrawCommands, Map<String, List<VxDrawCommand>> groupDrawCommands) {
        super(allDrawCommands, groupDrawCommands);
        this.parentBuffer = Objects.requireNonNull(parentBuffer, "Parent buffer cannot be null");
        this.offsetBytes = offsetBytes;
        this.sizeBytes = sizeBytes;
        this.baseVertex = (int) (offsetBytes / VxVertexLayout.STRIDE);

        // Initialize textures immediately upon creation to ensure they are ready for rendering.
        initializeTextures();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Binds the shared VAO of the parent arena buffer.
     */
    @Override
    public void setupVaoState() {
        this.parentBuffer.preRender();
    }

    /**
     * {@inheritDoc}
     * <p>
     * For an arena sub-mesh, the final vertex offset is the sum of its allocation base offset
     * and the local offset defined in the draw command.
     *
     * @param command The draw command to process.
     * @return The absolute vertex index in the shared VBO.
     */
    @Override
    public int getFinalVertexOffset(VxDrawCommand command) {
        return this.baseVertex + command.vertexOffset;
    }

    /**
     * Deletes this mesh handle and frees its associated memory block in the parent
     * {@link VxArenaBuffer}.
     */
    @Override
    public void delete() {
        if (!this.isDeleted) {
            this.parentBuffer.free(this);
            this.isDeleted = true;
        }
    }

    /**
     * Gets the byte offset of this mesh in the parent buffer.
     *
     * @return The offset in bytes.
     */
    public long getOffsetBytes() {
        return offsetBytes;
    }

    /**
     * Gets the total size of this mesh data.
     *
     * @return The size in bytes.
     */
    public long getSizeBytes() {
        return sizeBytes;
    }
}