package net.xmx.xbullet.model.objmodel;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.*;

public class OBJModel implements AutoCloseable {

    // Store pre-flattened vertex data for each material part
    private final Map<String, PreprocessedMeshPart> meshParts = new LinkedHashMap<>();

    private OBJModel(OBJLoader.ParsedObjModel parsedModel, ResourceLocation defaultTexture) {
        parsedModel.facesByMaterial.forEach((materialName, faces) -> {
            if (faces.isEmpty()) return;

            OBJLoader.Material material = parsedModel.materials.getOrDefault(materialName, new OBJLoader.Material());
            if (material.diffuseMap == null && defaultTexture != null) {
                material.diffuseMap = defaultTexture;
            }

            // Create a flat list of all vertices needed for this part
            List<Vertex> preprocessedVertices = new ArrayList<>(faces.size() * 3);
            for (int[] face : faces) {
                for (int i = 0; i < 3; i++) {
                    Vector3f pos = parsedModel.vertices.get(face[i * 3]);
                    Vector2f uv = face[i * 3 + 1] != -1 ? parsedModel.texCoords.get(face[i * 3 + 1]) : new Vector2f(0, 0);
                    Vector3f normal = face[i * 3 + 2] != -1 ? parsedModel.normals.get(face[i * 3 + 2]) : new Vector3f(0, 1, 0);
                    preprocessedVertices.add(new Vertex(pos, uv, normal));
                }
            }

            ResourceLocation texture = material.diffuseMap != null ? material.diffuseMap : MissingTextureAtlasSprite.getLocation();
            RenderType renderType = material.diffuseColor.w < 0.99f ? OBJRenderTypes.objTranslucent(texture) : OBJRenderTypes.objSolid(texture);

            meshParts.put(materialName, new PreprocessedMeshPart(preprocessedVertices, material, renderType));
        });
    }

    @Override
    public void close() {
        // Nothing to close, as we hold no GPU resources directly
    }

    /**
     * Renders the model by feeding the pre-processed vertex list into the MultiBufferSource.
     * This is much faster than parsing the OBJ structure every frame.
     */
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        render(poseStack, bufferSource, packedLight, packedOverlay, meshParts.keySet());
    }

    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay, Set<String> groupNames) {
        if (groupNames.isEmpty()) return;

        Matrix4f poseMatrix = poseStack.last().pose();
        Matrix3f normalMatrix = poseStack.last().normal();

        for (String groupName : groupNames) {
            PreprocessedMeshPart part = meshParts.get(groupName);
            if (part == null || !groupNames.contains(groupName)) continue;

            VertexConsumer consumer = bufferSource.getBuffer(part.renderType());
            var color = part.material().diffuseColor;

            // This is now a very tight loop over a flat list.
            for (Vertex v : part.vertices()) {
                consumer.vertex(poseMatrix, v.pos.x, v.pos.y, v.pos.z)
                        .color((int)(color.x * 255), (int)(color.y * 255), (int)(color.z * 255), (int)(color.w * 255))
                        .uv(v.uv.x, v.uv.y)
                        .overlayCoords(packedOverlay)
                        .uv2(packedLight)
                        .normal(normalMatrix, v.normal.x, v.normal.y, v.normal.z)
                        .endVertex();
            }
        }
    }

    // A simple record to hold the flattened vertex data
    private record Vertex(Vector3f pos, Vector2f uv, Vector3f normal) {}

    // A record to hold the pre-processed mesh data
    private record PreprocessedMeshPart(List<Vertex> vertices, OBJLoader.Material material, RenderType renderType) {}


    // Builder class
    public static class Builder {
        private final ResourceLocation modelLocation;
        private ResourceLocation defaultTextureLocation;
        private boolean loadMtl = true;

        public Builder(ResourceLocation modelLocation) { this.modelLocation = modelLocation; }
        public Builder withDefaultTexture(ResourceLocation textureLocation) { this.defaultTextureLocation = textureLocation; return this; }
        public Builder ignoreMtl() { this.loadMtl = false; return this; }

        public OBJModel build() throws Exception {
            OBJLoader.ParsedObjModel parsedModel = OBJLoader.loadModel(modelLocation, this.loadMtl);
            return new OBJModel(parsedModel, this.defaultTextureLocation);
        }
    }
}