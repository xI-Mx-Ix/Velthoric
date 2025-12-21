package net.timtaran.interactivemc.util;

import net.minecraft.resources.ResourceLocation;
import net.timtaran.interactivemc.init.InteractiveMC;

public class InteractiveMCIdentifier {
    public static String getTranslationKey(String category, String path) {
        return "%s.%s.%s".formatted(category, InteractiveMC.MOD_ID, path);
    }

    public static ResourceLocation get(String path) {
        return ResourceLocation.fromNamespaceAndPath(InteractiveMC.MOD_ID, path);
    }
}
