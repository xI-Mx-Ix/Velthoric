/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.misc.accessor;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accessor interface for the DebugScreenOverlay class.
 * This allows access to private fields and methods from outside the class.
 *
 * @author xI-Mx-Ix
 */
@Mixin(DebugScreenOverlay.class)
public interface MixinDebugScreenOverlayAccessor {

    /**
     * Provides access to the private 'font' field in DebugScreenOverlay.
     * @return The Font instance.
     */
    @Accessor("font")
    Font getFont();

    /**
     * Provides access to the private 'getSampleColor' method in DebugScreenOverlay.
     * The 'Invoker' annotation is used for methods, while 'Accessor' is used for fields.
     */
    @Invoker("getSampleColor")
    int invokeGetSampleColor(int value, int r, int g, int b);
}