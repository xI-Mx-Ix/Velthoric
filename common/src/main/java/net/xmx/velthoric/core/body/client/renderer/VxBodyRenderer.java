/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.body.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.xmx.velthoric.core.body.client.VxRenderState;
import net.xmx.velthoric.core.body.type.VxBody;

/**
 * An abstract base class for rendering a specific type of {@link VxBody}.
 * Concrete implementations of this class are responsible for the actual OpenGL drawing calls.
 *
 * @param <T> The specific type of VxBody this renderer can draw.
 * @author xI-Mx-Ix
 */
public abstract class VxBodyRenderer<T extends VxBody> {

    /**
     * Renders the physics body in the world.
     *
     * @param body         The specific body instance to render.
     * @param poseStack    The current pose stack for transformations.
     * @param bufferSource The buffer source for drawing.
     * @param partialTicks The fraction of the current tick.
     * @param packedLight  The calculated light value at the body's position.
     * @param renderState  The final interpolated state that should be rendered.
     */
    public abstract void render(T body, PoseStack poseStack, MultiBufferSource bufferSource, float partialTicks, int packedLight, VxRenderState renderState);
}