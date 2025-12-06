/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.renderer.model.parser;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.renderer.gl.VxMaterial;
import net.xmx.velthoric.renderer.VxRConstants;
import net.xmx.velthoric.renderer.model.raw.VxRawGroup;
import net.xmx.velthoric.renderer.model.raw.VxRawMesh;
import net.xmx.velthoric.renderer.model.raw.VxRawModel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;

/**
 * A parser for OBJ model files that produces a {@link VxRawModel}.
 * <p>
 * This parser extracts all geometry data into an editable format using a Structure-of-Arrays
 * approach for performance. It does not perform buffer flattening, but it does handle index parsing.
 *
 * @author xI-Mx-Ix
 */
public class VxObjParser {

    /**
     * Parses an OBJ model from a given {@link ResourceLocation}.
     *
     * @param location The location of the .obj file.
     * @return A {@link VxRawModel} containing the raw geometry data.
     * @throws IOException If the file cannot be read.
     */
    public static VxRawModel parse(ResourceLocation location) throws IOException {
        VxRawModel rawModel = new VxRawModel();

        String defaultGroup = "default";
        String defaultMaterial = "default";
        String currentGroupName = defaultGroup;
        String currentMaterialName = defaultMaterial;

        // Ensure default material exists
        rawModel.materials.put(defaultMaterial, new VxMaterial(defaultMaterial));

        try (InputStream stream = Minecraft.getInstance().getResourceManager().getResource(location).get().open();
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] tokens = line.trim().split("\\s+");
                if (tokens.length == 0) continue;

                String keyword = tokens[0];

                switch (keyword) {
                    case "v" -> parseVertex(tokens, rawModel.positions, rawModel.colors);
                    case "vt" -> parseVec2(tokens, rawModel.texCoords);
                    case "vn" -> parseVec3(tokens, rawModel.normals);
                    case "g", "o" -> {
                        currentGroupName = tokens.length > 1 ? tokens[1] : defaultGroup;
                    }
                    case "usemtl" -> {
                        currentMaterialName = tokens.length > 1 ? tokens[1] : defaultMaterial;
                        rawModel.materials.computeIfAbsent(currentMaterialName, VxMaterial::new);
                    }
                    case "mtllib" -> {
                        if (tokens.length > 1) {
                            String mtlPath = resolveRelativePath(location, tokens[1]);
                            Map<String, VxMaterial> loaded = loadMtl(location.withPath(mtlPath), location);
                            rawModel.materials.putAll(loaded);
                        }
                    }
                    case "f" -> {
                        VxRawGroup group = rawModel.getGroup(currentGroupName);
                        // Get the flat index mesh for the current material
                        VxRawMesh mesh = group.getMesh(currentMaterialName);
                        parseFace(tokens, mesh);
                    }
                }
            }
        }

        return rawModel;
    }

    // --- Helper Methods ---

    /**
     * Parses a vertex line "v x y z [r g b]".
     */
    private static void parseVertex(String[] tokens, FloatArrayList pos, FloatArrayList cols) {
        pos.add(Float.parseFloat(tokens[1]));
        pos.add(Float.parseFloat(tokens[2]));
        pos.add(Float.parseFloat(tokens[3]));

        if (tokens.length >= 7) {
            cols.add(Float.parseFloat(tokens[4]));
            cols.add(Float.parseFloat(tokens[5]));
            cols.add(Float.parseFloat(tokens[6]));
        }
    }

    /**
     * Parses a normal line "vn x y z".
     */
    private static void parseVec3(String[] tokens, FloatArrayList list) {
        list.add(Float.parseFloat(tokens[1]));
        list.add(Float.parseFloat(tokens[2]));
        list.add(Float.parseFloat(tokens[3]));
    }

    /**
     * Parses a UV line "vt u v". Inverts V for OpenGL.
     */
    private static void parseVec2(String[] tokens, FloatArrayList list) {
        list.add(Float.parseFloat(tokens[1]));
        list.add(1.0f - Float.parseFloat(tokens[2])); // Invert V
    }

    /**
     * Parses a face line "f v1/vt1/vn1 ...". Handles triangulation.
     */
    private static void parseFace(String[] tokens, VxRawMesh mesh) {
        int vertexCount = tokens.length - 1;
        if (vertexCount < 3) return;

        // Temporary arrays for fan triangulation indices
        int[] v = new int[vertexCount];
        int[] vt = new int[vertexCount];
        int[] vn = new int[vertexCount];

        for (int i = 0; i < vertexCount; i++) {
            String[] subIndices = tokens[i + 1].split("/");

            // OBJ indices are 1-based, convert to 0-based
            v[i] = Integer.parseInt(subIndices[0]) - 1;

            vt[i] = (subIndices.length > 1 && !subIndices[1].isEmpty())
                    ? Integer.parseInt(subIndices[1]) - 1 : -1;

            vn[i] = (subIndices.length > 2 && !subIndices[2].isEmpty())
                    ? Integer.parseInt(subIndices[2]) - 1 : -1;
        }

        // Triangulate (Triangle Fan)
        // Adds indices directly to the IntArrayList in VxRawMesh
        for (int i = 1; i < vertexCount - 1; ++i) {
            mesh.addFace(
                    v[0], v[i], v[i + 1],
                    vt[0], vt[i], vt[i + 1],
                    vn[0], vn[i], vn[i + 1]
            );
        }
    }

    private static Map<String, VxMaterial> loadMtl(ResourceLocation mtlLocation, ResourceLocation modelLocation) {
        try (InputStream stream = Minecraft.getInstance().getResourceManager().getResource(mtlLocation).get().open()) {
            return VxMtlParser.parse(stream, modelLocation);
        } catch (IOException e) {
            VxRConstants.LOGGER.warn("Could not load material library: {}", mtlLocation);
            return Map.of();
        }
    }

    private static String resolveRelativePath(ResourceLocation modelLocation, String relativePath) {
        String modelPath = modelLocation.getPath();
        if (modelPath.contains("/")) {
            String basePath = modelPath.substring(0, modelPath.lastIndexOf('/') + 1);
            return basePath + relativePath;
        }
        return relativePath;
    }
}