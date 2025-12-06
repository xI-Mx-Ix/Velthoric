/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.renderer.model.parser;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.renderer.gl.VxDrawCommand;
import net.xmx.velthoric.renderer.gl.VxMaterial;
import net.xmx.velthoric.renderer.VxRConstants;
import net.xmx.velthoric.renderer.mesh.VxMeshDefinition;
import net.xmx.velthoric.renderer.model.VxVertexBufferBuilder;
import net.xmx.velthoric.renderer.model.generator.VxNormalGenerator;
import net.xmx.velthoric.renderer.model.generator.VxTangentGenerator;
import org.joml.Vector3f;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

/**
 * A parser for OBJ model files that produces a generic {@link VxMeshDefinition}.
 * <p>
 * This parser respects the OBJ 'g' (group) and 'o' (object) tags. It segments the
 * mesh data into internal buckets, allowing the resulting {@link VxMeshDefinition}
 * to provide independent draw calls for each named group.
 *
 * @author xI-Mx-Ix
 */
public class VxObjParser {

    /**
     * Parses an OBJ model from a given {@link ResourceLocation}.
     *
     * @param location The location of the .obj file.
     * @return A {@link VxMeshDefinition} containing the geometry and grouped draw commands.
     * @throws IOException If the file cannot be read.
     */
    public static VxMeshDefinition parse(ResourceLocation location) throws IOException {
        // Raw global data pools
        FloatArrayList positions = new FloatArrayList();
        FloatArrayList texCoords = new FloatArrayList();
        FloatArrayList normals = new FloatArrayList();
        FloatArrayList vertexColors = new FloatArrayList();

        // Map structure: GroupName -> (MaterialName -> VertexBufferBuilder)
        // We must separate geometry not just by group, but also by material within that group.
        Map<String, Map<String, VxVertexBufferBuilder>> groupedBuilders = new LinkedHashMap<>();

        // Material cache
        Map<String, VxMaterial> globalMaterials = new HashMap<>();

        String defaultGroup = "default";
        String defaultMaterial = "default";

        String currentGroup = defaultGroup;
        String currentMaterial = defaultMaterial;

        // Initialize default containers
        globalMaterials.put(defaultMaterial, new VxMaterial(defaultMaterial));
        groupedBuilders.computeIfAbsent(currentGroup, k -> new LinkedHashMap<>())
                .put(currentMaterial, new VxVertexBufferBuilder());

        try (InputStream stream = Minecraft.getInstance().getResourceManager().getResource(location).get().open();
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] tokens = line.trim().split("\\s+");
                if (tokens.length == 0) continue;

                String keyword = tokens[0];

                switch (keyword) {
                    case "v" -> parseVertex(tokens, positions, vertexColors);
                    case "vt" -> parseVec2(tokens, texCoords);
                    case "vn" -> parseVec3(tokens, normals);
                    case "g", "o" -> {
                        // Switch context to a new group
                        currentGroup = tokens.length > 1 ? tokens[1] : defaultGroup;
                        groupedBuilders.computeIfAbsent(currentGroup, k -> new LinkedHashMap<>());
                    }
                    case "usemtl" -> {
                        // Switch context to a new material
                        currentMaterial = tokens.length > 1 ? tokens[1] : defaultMaterial;
                        globalMaterials.computeIfAbsent(currentMaterial, VxMaterial::new);
                    }
                    case "mtllib" -> {
                        if (tokens.length > 1) {
                            String mtlPath = resolveRelativePath(location, tokens[1]);
                            globalMaterials.putAll(loadMtl(location.withPath(mtlPath), location));
                        }
                    }
                    case "f" -> {
                        // Get the builder specifically for the current Group/Material pair
                        VxVertexBufferBuilder builder = groupedBuilders
                                .get(currentGroup)
                                .computeIfAbsent(currentMaterial, k -> new VxVertexBufferBuilder());

                        parseFace(tokens, positions, texCoords, normals, vertexColors, builder, globalMaterials.get(currentMaterial));
                    }
                }
            }
        }

        // --- Buffer Assembly ---

        // 1. Calculate total vertex count needed
        int totalVertexCount = 0;
        for (var groupEntry : groupedBuilders.values()) {
            for (var builder : groupEntry.values()) {
                totalVertexCount += builder.getVertexCount();
            }
        }

        if (totalVertexCount == 0) {
            return new VxMeshDefinition(ByteBuffer.allocate(0), Collections.emptyList(), Collections.emptyMap());
        }

        // 2. Allocate one single buffer for the whole model
        ByteBuffer combinedBuffer = ByteBuffer.allocateDirect(totalVertexCount * VxVertexBufferBuilder.STRIDE).order(ByteOrder.nativeOrder());

        Map<String, List<VxDrawCommand>> groupCommands = new HashMap<>();
        List<VxDrawCommand> allCommands = new ArrayList<>();

        int currentVertexOffset = 0;

        // 3. Flatten the builders into the single buffer and generate commands
        for (Map.Entry<String, Map<String, VxVertexBufferBuilder>> groupEntry : groupedBuilders.entrySet()) {
            String groupName = groupEntry.getKey();
            List<VxDrawCommand> commandsForThisGroup = new ArrayList<>();

            for (Map.Entry<String, VxVertexBufferBuilder> matEntry : groupEntry.getValue().entrySet()) {
                VxVertexBufferBuilder builder = matEntry.getValue();
                if (builder.isEmpty()) continue;

                // Write builder data to the combined buffer
                combinedBuffer.put(builder.build());

                // Create the draw command
                VxMaterial mat = globalMaterials.getOrDefault(matEntry.getKey(), new VxMaterial(defaultMaterial));
                VxDrawCommand cmd = new VxDrawCommand(mat, currentVertexOffset, builder.getVertexCount());

                commandsForThisGroup.add(cmd);
                allCommands.add(cmd);

                currentVertexOffset += builder.getVertexCount();
            }

            if (!commandsForThisGroup.isEmpty()) {
                groupCommands.put(groupName, commandsForThisGroup);
            }
        }

        // 4. Finalize buffer and calculate tangents
        combinedBuffer.flip();
        VxTangentGenerator.calculate(combinedBuffer, totalVertexCount);
        combinedBuffer.rewind();

        return new VxMeshDefinition(combinedBuffer, allCommands, groupCommands);
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
        } else {
            cols.add(1.0f);
            cols.add(1.0f);
            cols.add(1.0f);
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
    private static void parseFace(String[] tokens, FloatArrayList pos, FloatArrayList uv, FloatArrayList norm, FloatArrayList colors, VxVertexBufferBuilder builder, VxMaterial material) {
        // Note: tokens[0] is "f", vertices start at tokens[1]
        int vertexCount = tokens.length - 1;
        if (vertexCount < 3) return;

        int[] posIndices = new int[vertexCount];
        int[] uvIndices = new int[vertexCount];
        int[] normIndices = new int[vertexCount];
        boolean hasNormals = false;

        for (int i = 0; i < vertexCount; i++) {
            String[] subIndices = tokens[i + 1].split("/");
            posIndices[i] = Integer.parseInt(subIndices[0]) - 1;
            uvIndices[i] = (subIndices.length > 1 && !subIndices[1].isEmpty()) ? Integer.parseInt(subIndices[1]) - 1 : -1;
            if (subIndices.length > 2 && !subIndices[2].isEmpty()) {
                normIndices[i] = Integer.parseInt(subIndices[2]) - 1;
                hasNormals = true;
            } else {
                normIndices[i] = -1;
            }
        }

        Vector3f calculatedNormal = null;
        if (!hasNormals) {
            calculatedNormal = VxNormalGenerator.calculateFaceNormal(pos, posIndices[0], posIndices[1], posIndices[2]);
        }

        // Triangulate (Triangle Fan)
        for (int i = 1; i < vertexCount - 1; ++i) {
            builder.writeVertex(pos, uv, norm, colors, posIndices[0], uvIndices[0], normIndices[0], material.baseColorFactor, calculatedNormal);
            builder.writeVertex(pos, uv, norm, colors, posIndices[i], uvIndices[i], normIndices[i], material.baseColorFactor, calculatedNormal);
            builder.writeVertex(pos, uv, norm, colors, posIndices[i + 1], uvIndices[i + 1], normIndices[i + 1], material.baseColorFactor, calculatedNormal);
        }
    }

    /**
     * Loads a material library from the given path.
     */
    private static Map<String, VxMaterial> loadMtl(ResourceLocation mtlLocation, ResourceLocation modelLocation) {
        try (InputStream stream = Minecraft.getInstance().getResourceManager().getResource(mtlLocation).get().open()) {
            return VxMtlParser.parse(stream, modelLocation);
        } catch (IOException e) {
            VxRConstants.LOGGER.warn("Could not load material library: {}. Using default material.", mtlLocation);
            return new HashMap<>();
        }
    }

    /**
     * Resolves relative paths found in the OBJ file to full resource locations.
     */
    private static String resolveRelativePath(ResourceLocation modelLocation, String relativePath) {
        String modelPath = modelLocation.getPath();
        if (modelPath.contains("/")) {
            String basePath = modelPath.substring(0, modelPath.lastIndexOf('/') + 1);
            return basePath + relativePath;
        }
        return relativePath;
    }
}