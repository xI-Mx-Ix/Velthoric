package net.xmx.xbullet.model.objmodel;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OBJLoader {

    public static ParsedObjModel loadModel(ResourceLocation location) throws Exception {
        return loadModel(location, true);
    }

    public static ParsedObjModel loadModel(ResourceLocation location, boolean loadMtl) throws Exception {
        List<Vector3f> vertices = new ArrayList<>();
        List<Vector2f> texCoords = new ArrayList<>();
        List<Vector3f> normals = new ArrayList<>();
        Map<String, List<int[]>> facesByMaterial = new LinkedHashMap<>();
        Map<String, Material> materials = new HashMap<>();

        String currentMaterial = "default";
        facesByMaterial.put(currentMaterial, new ArrayList<>());

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Minecraft.getInstance().getResourceManager().getResourceOrThrow(location).open()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\\s+");
                switch (parts[0]) {
                    case "v":
                        vertices.add(new Vector3f(Float.parseFloat(parts[1]), Float.parseFloat(parts[2]), Float.parseFloat(parts[3])));
                        break;
                    case "vt":
                        texCoords.add(new Vector2f(Float.parseFloat(parts[1]), 1.0f - Float.parseFloat(parts[2])));
                        break;
                    case "vn":
                        normals.add(new Vector3f(Float.parseFloat(parts[1]), Float.parseFloat(parts[2]), Float.parseFloat(parts[3])));
                        break;
                    case "f":
                        List<int[]> currentFaces = facesByMaterial.get(currentMaterial);
                        for (int i = 2; i < parts.length - 1; i++) {
                            int[] face = new int[9];
                            parseFaceVertex(parts[1], face, 0);
                            parseFaceVertex(parts[i], face, 3);
                            parseFaceVertex(parts[i + 1], face, 6);
                            currentFaces.add(face);
                        }
                        break;
                    case "mtllib":
                        if (loadMtl) {
                            ResourceLocation mtlLocation = ResourceLocation.tryBuild(location.getNamespace(), location.getPath().substring(0, location.getPath().lastIndexOf('/') + 1) + parts[1]);
                            try {
                                materials = loadMaterials(mtlLocation);
                            } catch (Exception e) {
                                System.err.println("Konnte MTL-Datei nicht laden: " + mtlLocation + ". Wird ignoriert.");
                            }
                        }
                        break;
                    case "usemtl":
                        currentMaterial = parts[1];
                        if (!facesByMaterial.containsKey(currentMaterial)) {
                            facesByMaterial.put(currentMaterial, new ArrayList<>());
                        }
                        break;
                }
            }
        }

        return new ParsedObjModel(vertices, texCoords, normals, facesByMaterial, materials);
    }

    private static void parseFaceVertex(String part, int[] face, int offset) {
        String[] indices = part.split("/");
        face[offset] = Integer.parseInt(indices[0]) - 1;
        if (indices.length > 1 && !indices[1].isEmpty()) {
            face[offset + 1] = Integer.parseInt(indices[1]) - 1;
        } else {
            face[offset + 1] = -1;
        }
        if (indices.length > 2 && !indices[2].isEmpty()) {
            face[offset + 2] = Integer.parseInt(indices[2]) - 1;
        } else {
            face[offset + 2] = -1;
        }
    }

    private static Map<String, Material> loadMaterials(ResourceLocation location) throws Exception {
        Map<String, Material> materials = new HashMap<>();
        Material currentMaterial = null;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Minecraft.getInstance().getResourceManager().getResourceOrThrow(location).open()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\\s+", 2);
                switch (parts[0]) {
                    case "newmtl":
                        currentMaterial = new Material();
                        materials.put(parts[1], currentMaterial);
                        break;
                    case "Kd":
                        if (currentMaterial != null) {
                            String[] colorParts = parts[1].split("\\s+");
                            currentMaterial.diffuseColor = new Vector4f(
                                    Float.parseFloat(colorParts[0]),
                                    Float.parseFloat(colorParts[1]),
                                    Float.parseFloat(colorParts[2]),
                                    currentMaterial.diffuseColor.w
                            );
                        }
                        break;
                    case "d":
                    case "Tr":
                        if (currentMaterial != null) {
                            currentMaterial.diffuseColor.w = Float.parseFloat(parts[1]);
                        }
                        break;
                    case "map_Kd":
                        if (currentMaterial != null)
                            currentMaterial.diffuseMap = ResourceLocation.tryBuild(location.getNamespace(), parts[1]);
                        break;
                    case "map_Bump":
                    case "bump":
                        if (currentMaterial != null)
                            currentMaterial.normalMap = ResourceLocation.tryBuild(location.getNamespace(), parts[1]);
                        break;
                    case "map_Ks":
                        if (currentMaterial != null)
                            currentMaterial.specularMap = ResourceLocation.tryBuild(location.getNamespace(), parts[1]);
                        break;
                }
            }
        }
        return materials;
    }

    public static class ParsedObjModel {
        public final List<Vector3f> vertices;
        public final List<Vector2f> texCoords;
        public final List<Vector3f> normals;
        public final Map<String, List<int[]>> facesByMaterial;
        public final Map<String, Material> materials;

        ParsedObjModel(List<Vector3f> vertices, List<Vector2f> texCoords, List<Vector3f> normals, Map<String, List<int[]>> facesByMaterial, Map<String, Material> materials) {
            this.vertices = vertices;
            this.texCoords = texCoords;
            this.normals = normals;
            this.facesByMaterial = facesByMaterial;
            this.materials = materials;
        }
    }

    public static class Material {
        public Vector4f diffuseColor = new Vector4f(1, 1, 1, 1);
        public ResourceLocation diffuseMap;
        public ResourceLocation normalMap;
        public ResourceLocation specularMap;
        public transient int diffuseTextureId = -1;
        public transient int normalTextureId = -1;
        public transient int specularTextureId = -1;
    }
}