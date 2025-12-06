/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.renderer.mesh;

import com.mojang.blaze3d.vertex.PoseStack;

/**
 * Defines a common interface for any mesh that can be rendered via the queue.
 *
 * @author xI-Mx-Ix
 */
public interface IVxRenderableMesh {

    /**
     * Queues the mesh for rendering in the current frame.
     * The actual draw call will happen when the render queue is flushed.
     *
     * @param poseStack The current transformation stack.
     * @param packedLight The packed light value at the mesh's position.
     */
    void queueRender(PoseStack poseStack, int packedLight);

    /**
     * Releases any GPU resources associated with this mesh.
     */
    void delete();
}