/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.renderer.model.parser;

import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.renderer.gl.VxMaterial;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * A parser for .mtl files that extracts PBR material properties.
 * <p>
 * This implementation parses raw paths from the MTL file and resolves them relative
 * to the OBJ model's directory. It performs no automatic folder injection, allowing
 * the MTL file to dictate the exact relative structure.
 *
 * @author xI-Mx-Ix
 */
public class VxMtlParser {

    /**
     * Parses an input stream of a .mtl file.
     * <p>
     * This method extracts material names, scalar PBR factors (metallic/roughness),
     * and the albedo texture path.
     * <p>
     * Note: Explicit texture maps for Normal, Metallic, and Roughness defined in the MTL
     * (e.g., map_Bump, map_Pr) are ignored. The renderer generates LabPBR 1.3 compliant
     * textures dynamically based on the scalar {@code Pm} and {@code Pr} values found here.
     *
     * @param inputStream   The stream containing the .mtl file data.
     * @param modelLocation The resource location of the parent .obj model.
     * @return A map of material names to their corresponding {@link VxMaterial} objects.
     * @throws IOException If an I/O error occurs.
     */
    public static Map<String, VxMaterial> parse(InputStream inputStream, ResourceLocation modelLocation) throws IOException {
        Map<String, VxMaterial> materials = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            VxMaterial currentMaterial = null;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                String[] parts = line.split("\\s+", 2);
                if (parts.length < 2) continue;

                String keyword = parts[0];
                String data = parts[1].trim();

                if (keyword.equals("newmtl")) {
                    currentMaterial = new VxMaterial(data);
                    materials.put(data, currentMaterial);
                    continue;
                }

                if (currentMaterial == null) {
                    continue;
                }

                switch (keyword) {
                    // --- Base Color / Albedo ---
                    case "Kd" -> parseColor(data, currentMaterial.baseColorFactor);
                    case "d" -> currentMaterial.baseColorFactor[3] = parseFloat(data, 1.0f);
                    case "Tr" -> currentMaterial.baseColorFactor[3] = 1.0f - parseFloat(data, 0.0f);

                    // --- PBR Scalar Factors ---
                    case "Pm", "metallic" -> currentMaterial.metallicFactor = parseFloat(data, 0.0f);
                    case "Pr", "roughness" -> currentMaterial.roughnessFactor = parseFloat(data, 1.0f);

                    // --- Texture Maps ---
                    // Now assigns the resolved string path to the material's albedoPath field.
                    case "map_Kd" -> currentMaterial.albedoMap = resolveTexturePath(data, modelLocation);
                }
            }
        }
        return materials;
    }

    /**
     * Parses a string of color components (R G B) into a float array.
     * @param data The string containing space-separated float values.
     * @param target The float array (at least size 3) to store the result.
     */
    private static void parseColor(String data, float[] target) {
        String[] parts = data.split("\\s+");
        if (parts.length >= 3) {
            target[0] = parseFloat(parts[0], 1.0f);
            target[1] = parseFloat(parts[1], 1.0f);
            target[2] = parseFloat(parts[2], 1.0f);
        }
    }

    /**
     * Safely parses a float from a string, returning a default value on failure.
     * @param s The string to parse.
     * @param defaultValue The value to return if parsing fails.
     * @return The parsed float or the default value.
     */
    private static float parseFloat(String s, float defaultValue) {
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Resolves a texture path relative to the model's directory.
     *
     * @param lineData      The raw data string from the MTL line.
     * @param modelLocation The ResourceLocation of the OBJ model.
     * @return A ResourceLocation pointing to the texture.
     */
    private static ResourceLocation resolveTexturePath(String lineData, ResourceLocation modelLocation) {
        String[] tokens = lineData.trim().split("\\s+");

        // Extract filename (last token containing a dot, ignoring flags)
        String rawPath = tokens[tokens.length - 1];
        for (int i = tokens.length - 1; i >= 0; i--) {
            String t = tokens[i];
            if (t.contains(".") && !t.startsWith("-")) {
                rawPath = t;
                break;
            }
        }

        // Determine parent directory from the model location
        String path = modelLocation.getPath();
        String directory = "";
        if (path.contains("/")) {
            directory = path.substring(0, path.lastIndexOf('/') + 1);
        }

        // Construct resource location preserving the namespace
        return modelLocation.withPath(directory + rawPath);
    }
}