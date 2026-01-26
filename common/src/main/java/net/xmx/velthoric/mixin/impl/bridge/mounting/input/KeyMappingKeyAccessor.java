package net.xmx.velthoric.mixin.impl.bridge.mounting.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyMapping.class)
public interface KeyMappingKeyAccessor {
    @Accessor("key")
    InputConstants.Key velthoric_getKey();
}
