/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.mixin.impl.culling;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor mixin to retrieve the vanilla Frustum instance.
 * This is required to ensure that custom renderers use the exact same culling logic
 * as the rest of the game, preventing issues where objects disappear unexpectedly.
 *
 * @author xI-Mx-Ix
 */
@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {
    /**
     * Accesses the main culling frustum used by the LevelRenderer.
     *
     * @return The current Frustum instance.
     */
    @Accessor("cullingFrustum")
    Frustum velthoric_getCullingFrustum();
}