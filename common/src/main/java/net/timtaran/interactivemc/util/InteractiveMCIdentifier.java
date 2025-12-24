package net.timtaran.interactivemc.util;

import net.minecraft.resources.ResourceLocation;
import net.timtaran.interactivemc.init.InteractiveMC;

/**
 * Utils for generating mod-specific identifiers.
 *
 * @author timtaran
 */
public class InteractiveMCIdentifier {

    /**
     * Generates a translation key for the given category and path.
     * The returned key is in the format "category.mod_id.path".
     *
     * @param category The category of the translation key.
     * @param path The path of the translation key.
     * @return The generated translation key.
     */
    public static String getTranslationKey(String category, String path) {
        return "%s.%s.%s".formatted(category, InteractiveMC.MOD_ID, path);
    }

    /**
     * Returns a {@link ResourceLocation} with the given path and mod_id namespace.
     *
     * @param path The path of the resource location.
     * @return The generated resource location.
     */
    public static ResourceLocation get(String path) {
        return ResourceLocation.fromNamespaceAndPath(InteractiveMC.MOD_ID, path);
    }
}
