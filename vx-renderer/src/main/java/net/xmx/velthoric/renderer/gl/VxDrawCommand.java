/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.renderer.gl;

/**
 * Represents a single, self-contained draw call for a portion of a mesh.
 * It specifies which vertices to draw and which material to apply.
 *
 * @author xI-Mx-Ix
 */
public class VxDrawCommand {
    public final VxMaterial material;
    public final int vertexOffset;
    public final int vertexCount;

    /**
     * Constructs a new draw command.
     *
     * @param material The material to use for this draw call.
     * @param vertexOffset The starting vertex index within the mesh's vertex buffer.
     * @param vertexCount The number of vertices to draw.
     */
    public VxDrawCommand(VxMaterial material, int vertexOffset, int vertexCount) {
        this.material = material;
        this.vertexOffset = vertexOffset;
        this.vertexCount = vertexCount;
    }
}