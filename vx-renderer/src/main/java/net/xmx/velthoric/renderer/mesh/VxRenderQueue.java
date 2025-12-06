/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.renderer.mesh;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.xmx.velthoric.renderer.VxDrawCommand;
import net.xmx.velthoric.renderer.VxRConstants;
import net.xmx.velthoric.renderer.VxShaderDetector;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;
import java.util.Arrays;

/**
 * A high-performance render queue that batches render calls to minimize state changes
 * and object allocations.
 * <p>
 * It uses a Structure of Arrays (SoA) approach to store render data (matrices, light, mesh references)
 * contiguously in memory. The actual rendering logic (Vanilla vs Shaderpack) is implemented here,
 * iterating over the queued data in a single pass per frame.
 * <p>
 * This class is a Singleton to ensure global access from any mesh entity.
 *
 * @author xI-Mx-Ix
 */
public class VxRenderQueue {

    private static VxRenderQueue instance;

    // Initial capacity for the queue arrays.
    private static final int INITIAL_CAPACITY = 1024;

    // Current number of items in the queue.
    private int count = 0;

    // Current maximum capacity of the arrays.
    private int capacity = INITIAL_CAPACITY;

    // --- Structure of Arrays (SoA) Storage ---
    // These arrays store render data contiguously to optimize memory access.
    private VxAbstractRenderableMesh[] meshes;
    private Matrix4f[] modelMatrices;
    private Matrix3f[] normalMatrices;
    private int[] packedLights;

    // --- Reusable Buffers and Math Objects ---
    // Static objects to prevent allocation during the render loop.
    private static final FloatBuffer MATRIX_BUFFER_9 = BufferUtils.createFloatBuffer(9);
    private static final int AT_UV2 = 4;

    /**
     * Standard Minecraft light direction 0 (Top-Left-Front), normalized.
     */
    private static final Vector3f VANILLA_LIGHT0 = new Vector3f(0.2f, 1.0f, -0.7f).normalize();

    /**
     * Standard Minecraft light direction 1 (Bottom-Right-Back), normalized.
     */
    private static final Vector3f VANILLA_LIGHT1 = new Vector3f(-0.2f, 1.0f, 0.7f).normalize();

    /**
     * Reusable scratch matrix for normal calculations.
     */
    private static final Matrix3f AUX_NORMAL_MAT = new Matrix3f();

    /**
     * Reusable scratch vector for Light 0 calculations.
     */
    private static final Vector3f AUX_LIGHT0 = new Vector3f();

    /**
     * Reusable scratch vector for Light 1 calculations.
     */
    private static final Vector3f AUX_LIGHT1 = new Vector3f();

    /**
     * Private constructor for Singleton pattern.
     * Pre-allocates the arrays and the matrix objects within them.
     */
    private VxRenderQueue() {
        this.meshes = new VxAbstractRenderableMesh[INITIAL_CAPACITY];
        this.modelMatrices = new Matrix4f[INITIAL_CAPACITY];
        this.normalMatrices = new Matrix3f[INITIAL_CAPACITY];
        this.packedLights = new int[INITIAL_CAPACITY];

        // Pre-allocate matrix objects. We will use .set() later to avoid new allocations.
        for (int i = 0; i < INITIAL_CAPACITY; i++) {
            this.modelMatrices[i] = new Matrix4f();
            this.normalMatrices[i] = new Matrix3f();
        }
    }

    /**
     * Gets the singleton instance of the render queue.
     * @return The global VxRenderQueue instance.
     */
    public static synchronized VxRenderQueue getInstance() {
        if (instance == null) {
            instance = new VxRenderQueue();
        }
        return instance;
    }

    /**
     * Resets the queue counter to zero.
     * This should be called at the start of the frame.
     */
    public void reset() {
        this.count = 0;
    }

    /**
     * Queues a mesh for rendering in the current frame.
     * Copies the current state from the PoseStack into the pre-allocated SoA arrays.
     *
     * @param mesh The mesh to render.
     * @param poseStack The current transformation stack.
     * @param packedLight The packed light value.
     */
    public void add(VxAbstractRenderableMesh mesh, PoseStack poseStack, int packedLight) {
        if (mesh == null || mesh.isDeleted()) return;

        ensureCapacity(count + 1);

        // Store mesh reference
        this.meshes[count] = mesh;

        // Copy matrix data into the pre-allocated objects using .set() (Zero Allocation)
        this.modelMatrices[count].set(poseStack.last().pose());
        this.normalMatrices[count].set(poseStack.last().normal());

        // Store primitive light value
        this.packedLights[count] = packedLight;

        count++;
    }

    /**
     * Ensures the arrays have enough space for new elements.
     * Grows by 50% if needed, allocating new matrix objects for the new slots only.
     *
     * @param minCapacity The minimum required capacity.
     */
    private void ensureCapacity(int minCapacity) {
        if (minCapacity > capacity) {
            int newCapacity = Math.max(capacity * 3 / 2, minCapacity);

            // Resize arrays
            this.meshes = Arrays.copyOf(this.meshes, newCapacity);
            this.packedLights = Arrays.copyOf(this.packedLights, newCapacity);

            Matrix4f[] newModelMats = new Matrix4f[newCapacity];
            Matrix3f[] newNormalMats = new Matrix3f[newCapacity];

            // Copy existing matrix references
            System.arraycopy(this.modelMatrices, 0, newModelMats, 0, capacity);
            System.arraycopy(this.normalMatrices, 0, newNormalMats, 0, capacity);

            // Allocate NEW objects only for the NEW slots
            for (int i = capacity; i < newCapacity; i++) {
                newModelMats[i] = new Matrix4f();
                newNormalMats[i] = new Matrix3f();
            }

            this.modelMatrices = newModelMats;
            this.normalMatrices = newNormalMats;
            this.capacity = newCapacity;
        }
    }

    /**
     * Flushes the queue, executing all render calls in a single batch.
     * This iterates over the SoA data and executes the rendering logic.
     */
    public void flush() {
        if (count == 0) return;

        RenderSystem.assertOnRenderThread();
        VxGlState.saveCurrentState();

        try {
            // Detect rendering pipeline and dispatch to the correct batch method
            if (VxShaderDetector.isShaderpackActive()) {
                renderBatchShaderpack();
            } else {
                renderBatchVanilla();
            }
        } finally {
            VxGlState.restorePreviousState();
            Arrays.fill(this.meshes, 0, count, null);
            this.count = 0;
        }
    }

    /**
     * Renders the batch using the Vanilla pipeline.
     * Includes lighting correction for static VBOs.
     */
    private void renderBatchVanilla() {
        // 1. Setup Common Global State (Uniforms that don't change per mesh)
        ShaderInstance shader = setupCommonRenderState();
        if (shader == null) return;

        // Bind Minecraft's lightmap texture to texture unit 2.
        Minecraft.getInstance().gameRenderer.lightTexture().turnOnLightLayer();

        // Set up all samplers before applying the shader for the first time.
        for (int i = 0; i < 12; ++i) {
            shader.setSampler("Sampler" + i, RenderSystem.getShaderTexture(i));
        }

        // 2. Iterate over queued items
        for (int i = 0; i < count; i++) {
            VxAbstractRenderableMesh mesh = this.meshes[i];
            Matrix4f modelMat = this.modelMatrices[i];
            Matrix3f normalMat = this.normalMatrices[i];
            int packedLight = this.packedLights[i];

            // --- Lighting Correction Start ---
            // 1. Retrieve the current normal matrix (Model -> View rotation).
            AUX_NORMAL_MAT.set(normalMat);

            // 2. Invert the matrix to get View -> Model rotation.
            // For pure rotation matrices, transpose is equivalent to inverse.
            AUX_NORMAL_MAT.transpose();

            // 3. Transform the global light vectors into the local model space.
            AUX_LIGHT0.set(VANILLA_LIGHT0).mul(AUX_NORMAL_MAT);
            AUX_LIGHT1.set(VANILLA_LIGHT1).mul(AUX_NORMAL_MAT);

            // 4. Upload the transformed light directions to the shader.
            if (shader.LIGHT0_DIRECTION != null) shader.LIGHT0_DIRECTION.set(AUX_LIGHT0);
            if (shader.LIGHT1_DIRECTION != null) shader.LIGHT1_DIRECTION.set(AUX_LIGHT1);
            // --- Lighting Correction End ---

            // Update ModelView Matrix uniform
            if (shader.MODEL_VIEW_MATRIX != null) shader.MODEL_VIEW_MATRIX.set(modelMat);

            // Upload Normal Matrix uniform
            int normalMatrixLocation = Uniform.glGetUniformLocation(shader.getId(), "NormalMat");
            if (normalMatrixLocation != -1) {
                MATRIX_BUFFER_9.position(0);
                normalMat.get(MATRIX_BUFFER_9);
                RenderSystem.glUniformMatrix3(normalMatrixLocation, false, MATRIX_BUFFER_9);
            }

            // Bind Mesh VAO (Delegated to mesh implementation)
            mesh.setupVaoState();

            // Upload Lightmap UV
            GL30.glDisableVertexAttribArray(AT_UV2);
            GL30.glVertexAttribI2i(AT_UV2, packedLight & 0xFFFF, packedLight >> 16);

            // Execute Draw Commands for this mesh
            for (VxDrawCommand command : mesh.getDrawCommands()) {
                RenderSystem.setShaderTexture(0, command.material.albedoMapGlId);

                if (shader.COLOR_MODULATOR != null) {
                    shader.COLOR_MODULATOR.set(command.material.baseColorFactor);
                }

                // Update Sampler0 to point to the new texture for this group.
                shader.setSampler("Sampler0", command.material.albedoMapGlId);

                shader.apply();
                GL11.glDrawArrays(GL11.GL_TRIANGLES, mesh.getFinalVertexOffset(command), command.vertexCount);
            }

            // Re-enable UV2 array for safety
            GL30.glEnableVertexAttribArray(AT_UV2);
        }

        shader.clear();
    }

    /**
     * Renders the batch using the Shaderpack pipeline (e.g., Iris).
     * Uses PBR textures and standard normal matrix handling.
     */
    private void renderBatchShaderpack() {
        // 1. Setup Common Global State
        ShaderInstance shader = setupCommonRenderState();
        if (shader == null) return;

        // Set up all samplers before applying the shader.
        for (int i = 0; i < 12; ++i) {
            shader.setSampler("Sampler" + i, RenderSystem.getShaderTexture(i));
        }
        shader.apply();

        // 2. Iterate over queued items
        for (int i = 0; i < count; i++) {
            VxAbstractRenderableMesh mesh = this.meshes[i];
            Matrix4f modelMat = this.modelMatrices[i];
            Matrix3f normalMat = this.normalMatrices[i];
            int packedLight = this.packedLights[i];

            // Update ModelView Matrix uniform
            if (shader.MODEL_VIEW_MATRIX != null) shader.MODEL_VIEW_MATRIX.set(modelMat);

            // Upload Normal Matrix uniform
            int normalMatrixLocation = Uniform.glGetUniformLocation(shader.getId(), "NormalMat");
            if (normalMatrixLocation != -1) {
                MATRIX_BUFFER_9.position(0);
                normalMat.get(MATRIX_BUFFER_9);
                RenderSystem.glUniformMatrix3(normalMatrixLocation, false, MATRIX_BUFFER_9);
            }

            // Bind Mesh VAO (Delegated to mesh implementation)
            mesh.setupVaoState();

            // Upload Lightmap UV
            GL30.glDisableVertexAttribArray(AT_UV2);
            GL30.glVertexAttribI2i(AT_UV2, packedLight & 0xFFFF, packedLight >> 16);

            // Execute Draw Commands for this mesh
            for (VxDrawCommand command : mesh.getDrawCommands()) {
                // Bind all PBR textures required by shaderpacks.
                RenderSystem.setShaderTexture(0, command.material.albedoMapGlId);
                RenderSystem.setShaderTexture(1, command.material.normalMapGlId);
                RenderSystem.setShaderTexture(3, command.material.metallicMapGlId);
                RenderSystem.setShaderTexture(4, command.material.roughnessMapGlId);

                if (shader.COLOR_MODULATOR != null) {
                    shader.COLOR_MODULATOR.set(command.material.baseColorFactor);
                }
                shader.apply(); // Re-apply shader if uniforms like COLOR_MODULATOR changed.

                GL11.glDrawArrays(GL11.GL_TRIANGLES, mesh.getFinalVertexOffset(command), command.vertexCount);
            }

            // Re-enable UV2 array for safety
            GL30.glEnableVertexAttribArray(AT_UV2);
        }

        shader.clear();
    }

    /**
     * Sets up the common render state and shader uniforms used by both rendering paths.
     * This is called once per batch flush.
     *
     * @return The configured {@link ShaderInstance} to be used for rendering, or null if setup fails.
     */
    private ShaderInstance setupCommonRenderState() {
        // --- Common OpenGL State ---
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        // --- Get and Set Shader ---
        ShaderInstance shader = GameRenderer.getRendertypeEntitySolidShader();
        if (shader == null) {
            VxRConstants.LOGGER.error("Failed to render mesh batch: RendertypeEntitySolidShader is null.");
            return null;
        }
        RenderSystem.setShader(() -> shader);

        // --- Common Uniform Setup (Constant for the frame/pass) ---
        if (shader.PROJECTION_MATRIX != null) shader.PROJECTION_MATRIX.set(RenderSystem.getProjectionMatrix());
        if (shader.TEXTURE_MATRIX != null) shader.TEXTURE_MATRIX.set(RenderSystem.getTextureMatrix());
        if (shader.COLOR_MODULATOR != null) shader.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
        if (shader.FOG_START != null) shader.FOG_START.set(RenderSystem.getShaderFogStart());
        if (shader.FOG_END != null) shader.FOG_END.set(RenderSystem.getShaderFogEnd());
        if (shader.FOG_COLOR != null) shader.FOG_COLOR.set(RenderSystem.getShaderFogColor());
        if (shader.FOG_SHAPE != null) shader.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getIndex());
        if (shader.GAME_TIME != null) shader.GAME_TIME.set(RenderSystem.getShaderGameTime());
        if (shader.GLINT_ALPHA != null) shader.GLINT_ALPHA.set(RenderSystem.getShaderGlintAlpha());
        if (shader.SCREEN_SIZE != null) {
            Window window = Minecraft.getInstance().getWindow();
            shader.SCREEN_SIZE.set((float) window.getWidth(), (float) window.getHeight());
        }

        RenderSystem.setupShaderLights(shader);
        return shader;
    }

    /**
     * A private utility class for safely managing and isolating critical OpenGL state.
     * <p>
     * This class provides a simple mechanism to save the current bindings for Vertex Array Objects (VAO),
     * Vertex Buffer Objects (VBO), and Element Buffer Objects (EBO) before custom rendering operations
     * and restore them afterward.
     */
    private static class VxGlState {
        private static int previousVaoId = -1;
        private static int previousVboId = -1;
        private static int previousEboId = -1;

        /**
         * Queries and stores the currently bound VAO, VBO, and EBO.
         */
        public static void saveCurrentState() {
            previousVaoId = GL30.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            previousVboId = GL15.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
            previousEboId = GL15.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
        }

        /**
         * Restores the VAO, VBO, and EBO that were saved by the last call to {@link #saveCurrentState()}.
         */
        public static void restorePreviousState() {
            if (previousVaoId != -1) {
                GL30.glBindVertexArray(previousVaoId);
                previousVaoId = -1;
            }
            if (previousVboId != -1) {
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousVboId);
                previousVboId = -1;
            }
            if (previousEboId != -1) {
                GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, previousEboId);
                previousEboId = -1;
            }
        }
    }
}