/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.vehicle.part;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * Defines how a specific {@link VxPart} should be rendered.
 * Implementations of this class handle drawing models, applying textures, etc.
 *
 * @param <T> The type of VxPart this renderer handles.
 * @author xI-Mx-Ix
 */
@Environment(EnvType.CLIENT)
public abstract class VxPartRenderer<T extends VxPart> {

    /**
     * Renders the vehicle part.
     * The PoseStack is already translated to the part's world position and rotated
     * according to the vehicle's orientation and the part's local rotation.
     *
     * @param part         The part being rendered.
     * @param poseStack    The matrix stack for rendering transformations.
     * @param bufferSource The source of render buffers.
     * @param partialTicks The partial tick time for interpolation.
     * @param packedLight  The light value at the part's position.
     */
    public abstract void render(T part, PoseStack poseStack, MultiBufferSource bufferSource, float partialTicks, int packedLight);
}