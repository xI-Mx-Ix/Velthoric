/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.bridge.mounting.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Mixin accessor to access key value of KeyMapping.
 *
 * @author timtaran
 */
@Mixin(KeyMapping.class)
public interface KeyMappingKeyAccessor {
    @Accessor("key")
    InputConstants.Key velthoric_getKey();
}
