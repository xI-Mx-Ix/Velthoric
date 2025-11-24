/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.debug;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Mixin class to access the renderHitBoxes field of the EntityRenderDispatcher class.
 * <p>
 * This mixin is used to access the renderHitBoxes field of the EntityRenderDispatcher class.
 * </p>
 *
 * @author xI-Mx-Ix
 */
@Mixin(EntityRenderDispatcher.class)
public interface EntityRenderDispatcherAccessor {

    @Accessor("renderHitBoxes")
    boolean getRenderHitBoxes();
}
