package net.timtaran.interactivemc.init.registries;

import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import net.minecraft.client.KeyMapping;
import net.timtaran.interactivemc.util.InteractiveMCIdentifier;

public class KeyMappings {
    public static final String KEYBIND_CATEGORY = InteractiveMCIdentifier.getTranslationKey("category", "vrbindings");
    public static final KeyMapping GRAB_KEYMAPPING = new KeyMapping(
            InteractiveMCIdentifier.getTranslationKey("key", "grab"),
            InputConstants.Type.KEYSYM,
            -1,
            KEYBIND_CATEGORY
    );

    public static void init() {
        KeyMappingRegistry.register(GRAB_KEYMAPPING);
    }
}
