package net.xmx.vortex.model.objmodel;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OBJModel implements AutoCloseable {

    private final Map<RenderType, VertexBuffer> buffers = new HashMap<>();

    private OBJModel(OBJLoader.ParsedObjModel parsedModel, ResourceLocation defaultTexture) {
        Map<String, OBJLoader.Material> materials = parsedModel.materials();
        if (!materials.containsKey("default")) {
            materials.put("default", new OBJLoader.Material());
        }

        Map<RenderType, BufferBuilder> builders = new HashMap<>();

        for (Map.Entry<String, List<int[]>> entry : parsedModel.facesByMaterial().entrySet()) {
            String materialName = entry.getKey();
            List<int[]> faces = entry.getValue();
            if (faces.isEmpty()) continue;

            OBJLoader.Material material = materials.getOrDefault(materialName, new OBJLoader.Material());
            ResourceLocation texture = material.diffuseMap != null ? material.diffuseMap : defaultTexture;
            if (texture == null) {
                texture = MissingTextureAtlasSprite.getLocation();
            }

            // Verwende einen passenden RenderType. entityTranslucent ist eine gute Wahl.
            // Der zweite Parameter `true` ist für "outline", was Standard ist.
            RenderType renderType = RenderType.entityTranslucent(texture, true);

            BufferBuilder builder = builders.computeIfAbsent(renderType, rt -> {
                BufferBuilder bb = new BufferBuilder(rt.bufferSize());
                bb.begin(rt.mode(), rt.format());
                return bb;
            });

            for (int[] face : faces) {
                for (int i = 0; i < 3; i++) {
                    int vertexIndex = face[i * 3];
                    int uvIndex = face[i * 3 + 1];
                    int normalIndex = face[i * 3 + 2];

                    Vector3f pos = parsedModel.vertices().get(vertexIndex);
                    Vector2f uv = (uvIndex != -1) ? parsedModel.texCoords().get(uvIndex) : new Vector2f(0, 0);
                    Vector3f normal = (normalIndex != -1) ? parsedModel.normals().get(normalIndex) : new Vector3f(0, 1, 0);
                    int r = (int) (material.diffuseColor.x() * 255);
                    int g = (int) (material.diffuseColor.y() * 255);
                    int b = (int) (material.diffuseColor.z() * 255);
                    int a = (int) (material.diffuseColor.w() * 255);

                    // KORREKTUR: Fülle ALLE vom VertexFormat geforderten Elemente.
                    builder.vertex(pos.x, pos.y, pos.z)
                            .color(r, g, b, a)
                            .uv(uv.x, uv.y)
                            .overlayCoords(OverlayTexture.NO_OVERLAY) // Platzhalter für Overlay
                            .uv2(15728880) // Platzhalter für Licht (volles Licht)
                            .normal(normal.x, normal.y, normal.z)
                            .endVertex();
                }
            }
        }

        // VBOs erstellen und hochladen
        for (Map.Entry<RenderType, BufferBuilder> entry : builders.entrySet()) {
            RenderType renderType = entry.getKey();
            BufferBuilder.RenderedBuffer renderedBuffer = entry.getValue().endOrDiscardIfEmpty();

            if (renderedBuffer != null && renderedBuffer.drawState().vertexCount() > 0) {
                VertexBuffer vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
                // KORREKTUR: Binde das VAO, das zum VBO gehört, DIREKT vor dem Upload.
                vbo.bind();
                vbo.upload(renderedBuffer);
                // Wichtig: Die Bindung wird erst am Ende aller Operationen aufgehoben.
                this.buffers.put(renderType, vbo);
            }
        }

        // WICHTIG: Hebe die Bindung des LETZTEN VAOs auf, nachdem die Schleife beendet ist,
        // um den OpenGL-Zustand für den Rest des Spiels sauber zu hinterlassen.
        VertexBuffer.unbind();
    }

    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (buffers.isEmpty()) {
            return;
        }

        Matrix4f projectionMatrix = RenderSystem.getProjectionMatrix();

        for (Map.Entry<RenderType, VertexBuffer> entry : this.buffers.entrySet()) {
            RenderType renderType = entry.getKey();
            VertexBuffer vbo = entry.getValue();

            // Dieser Aufruf sorgt dafür, dass der Render-Batch geflusht wird und der korrekte
            // RenderState (Shader, Blending etc.) für unseren RenderType aktiv wird.
            bufferSource.getBuffer(renderType);

            // Der Shader, der zu diesem RenderType gehört. Wir holen ihn uns zur Sicherheit explizit.
            var shader = GameRenderer.getRendertypeEntityTranslucentShader();

            // Die `drawWithShader` Methode ist die stabilste. Sie setzt alle Uniforms korrekt.
            // Die `packedLight` und `packedOverlay` Werte werden intern von `RenderSystem` geholt,
            // welche durch den `MultiBufferSource`-Kontext aktuell sein sollten.
            vbo.drawWithShader(poseStack.last().pose(), projectionMatrix, shader);
        }
    }

    @Override
    public void close() {
        // RenderSystem-Aufrufe müssen auf dem Render-Thread erfolgen
        RenderSystem.runAsFancy(() -> {
            for (VertexBuffer buffer : buffers.values()) {
                buffer.close();
            }
            buffers.clear();
        });
    }

    public static class Builder {
        private final ResourceLocation modelLocation;
        private ResourceLocation defaultTextureLocation;
        private boolean loadMtl = true;

        public Builder(ResourceLocation modelLocation) { this.modelLocation = modelLocation; }
        public Builder withDefaultTexture(ResourceLocation textureLocation) { this.defaultTextureLocation = textureLocation; return this; }
        public Builder ignoreMtl() { this.loadMtl = false; return this; }

        public OBJModel build() throws Exception {
            return new OBJModel(OBJLoader.loadModel(modelLocation, this.loadMtl), this.defaultTextureLocation);
        }
    }
}