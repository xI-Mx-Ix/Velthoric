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
import net.xmx.velthoric.renderer.VxRConstants;
import net.xmx.velthoric.renderer.gl.VxDrawCommand;
import net.xmx.velthoric.renderer.gl.VxGlState;
import net.xmx.velthoric.renderer.gl.VxMaterial;
import net.xmx.velthoric.renderer.util.VxShaderDetector;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
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
    // Using BufferUtils creates a direct buffer, which is more efficient for LWJGL interop.
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
     * Reusable scratch matrix for the combined Model-View transformation.
     */
    private static final Matrix4f AUX_MODEL_VIEW = new Matrix4f();

    /**
     * Reusable scratch matrix for the combined Normal transformation (View Rotation * Model Normal).
     */
    private static final Matrix3f AUX_NORMAL_VIEW = new Matrix3f();

    /**
     * Reusable scratch matrix to hold the rotation component of the camera's view matrix.
     */
    private static final Matrix3f VIEW_ROTATION = new Matrix3f();

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
     *
     * @param viewMatrix       The camera view matrix (transforms World to Camera space).
     * @param projectionMatrix The projection matrix.
     */
    public void flush(Matrix4f viewMatrix, Matrix4f projectionMatrix) {
        if (count == 0) return;

        RenderSystem.assertOnRenderThread();
        // Save the previous GL state to prevent conflicts with other renderers
        VxGlState.saveCurrentState();

        try {
            // Extract the rotation component from the view matrix.
            // This is required to correctly rotate normal vectors into view space.
            viewMatrix.get3x3(VIEW_ROTATION);

            // Detect rendering pipeline and dispatch to the correct batch method
            if (VxShaderDetector.isShaderpackActive()) {
                renderBatchShaderpack(viewMatrix, projectionMatrix);
            } else {
                renderBatchVanilla(viewMatrix, projectionMatrix);
            }
        } finally {
            // Restore the GL state and clear references to allow GC
            VxGlState.restorePreviousState();
            Arrays.fill(this.meshes, 0, count, null);
            this.count = 0;
        }
    }

    /**
     * Renders the batch using the Vanilla pipeline.
     * Includes lighting correction for static VBOs.
     *
     * @param viewMatrix       The camera view matrix.
     * @param projectionMatrix The projection matrix.
     */
    private void renderBatchVanilla(Matrix4f viewMatrix, Matrix4f projectionMatrix) {
        // 1. Setup Common Global State (Uniforms that don't change per mesh)
        ShaderInstance shader = setupCommonRenderState(projectionMatrix);
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

            // Compute the Model-View Matrix: ViewMatrix * ModelMatrix
            // This transforms the object from local space to camera space.
            AUX_MODEL_VIEW.set(viewMatrix).mul(modelMat);

            // Compute the View-Normal Matrix: ViewRotation * NormalMatrix
            // This rotates the normals according to the camera's orientation.
            AUX_NORMAL_VIEW.set(VIEW_ROTATION).mul(normalMat);

            // --- Lighting Correction Start ---
            // 1. Use the view-adjusted normal matrix for lighting calculations.
            AUX_NORMAL_MAT.set(AUX_NORMAL_VIEW);

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

            // Update ModelView Matrix uniform with the combined matrix
            if (shader.MODEL_VIEW_MATRIX != null) shader.MODEL_VIEW_MATRIX.set(AUX_MODEL_VIEW);

            // Upload Normal Matrix uniform
            int normalMatrixLocation = Uniform.glGetUniformLocation(shader.getId(), "NormalMat");
            if (normalMatrixLocation != -1) {
                MATRIX_BUFFER_9.clear(); // Reset buffer position
                // Use the view-adjusted normals
                AUX_NORMAL_VIEW.get(MATRIX_BUFFER_9);
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
     * Renders the queued meshes using a pipeline compatible with Iris shaderpacks.
     * <p>
     * Unlike the vanilla path, this method dynamically inspects the active OpenGL shader program
     * to determine which texture units are assigned to the "normals" and "specular" samplers.
     * This ensures PBR maps are bound to the correct slots expected by the specific shaderpack
     * in use, rather than assuming fixed units (e.g., 1 or 3).
     *
     * @param viewMatrix       The camera view matrix.
     * @param projectionMatrix The projection matrix.
     */
    private void renderBatchShaderpack(Matrix4f viewMatrix, Matrix4f projectionMatrix) {
        // 1. Setup global state (Depth, Cull, Blending) and bind the base shader
        ShaderInstance shader = setupCommonRenderState(projectionMatrix);
        if (shader == null) return;

        // Ensure all vanilla samplers are initially set up to match the shader's expectations
        for (int i = 0; i < 12; ++i) {
            shader.setSampler("Sampler" + i, RenderSystem.getShaderTexture(i));
        }
        shader.apply();

        // 2. Dynamic PBR Unit Resolution
        // Query the active shader program to find where it expects PBR textures.
        int programId = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);

        int normalUniformLoc = GL20.glGetUniformLocation(programId, "normals");
        int specularUniformLoc = GL20.glGetUniformLocation(programId, "specular");

        // Retrieve the texture unit index (integer value) associated with the sampler uniforms.
        // If the uniform is not present, default to -1 to skip binding.
        int targetNormalUnit = (normalUniformLoc != -1) ? GL20.glGetUniformi(programId, normalUniformLoc) : -1;
        int targetSpecularUnit = (specularUniformLoc != -1) ? GL20.glGetUniformi(programId, specularUniformLoc) : -1;

        // 3. Render Loop
        for (int i = 0; i < count; i++) {
            VxAbstractRenderableMesh mesh = this.meshes[i];
            Matrix4f modelMat = this.modelMatrices[i];
            Matrix3f normalMat = this.normalMatrices[i];
            int packedLight = this.packedLights[i];

            // Calculate Model-View Matrix
            AUX_MODEL_VIEW.set(viewMatrix).mul(modelMat);

            // Calculate Normal Matrix (View Rotation * Model Normal Rotation)
            // This is required for correct lighting calculations in view space.
            AUX_NORMAL_VIEW.set(VIEW_ROTATION).mul(normalMat);

            // Upload Standard Uniforms
            if (shader.MODEL_VIEW_MATRIX != null) {
                shader.MODEL_VIEW_MATRIX.set(AUX_MODEL_VIEW);
            }

            // Upload Normal Matrix
            // Shaders typically use "NormalMat" or "normalMatrix" to transform normals.
            int normalMatrixLocation = Uniform.glGetUniformLocation(programId, "NormalMat");
            if (normalMatrixLocation == -1) {
                normalMatrixLocation = Uniform.glGetUniformLocation(programId, "normalMatrix");
            }

            if (normalMatrixLocation != -1) {
                MATRIX_BUFFER_9.clear();
                AUX_NORMAL_VIEW.get(MATRIX_BUFFER_9);
                RenderSystem.glUniformMatrix3(normalMatrixLocation, false, MATRIX_BUFFER_9);
            }

            // Prepare Mesh Data
            mesh.setupVaoState();

            // Upload Lightmap Coordinates (Overlay/Lightmap are usually generic attributes)
            GL30.glDisableVertexAttribArray(AT_UV2);
            GL30.glVertexAttribI2i(AT_UV2, packedLight & 0xFFFF, packedLight >> 16);

            // Process Draw Commands
            for (VxDrawCommand command : mesh.getDrawCommands()) {
                VxMaterial material = command.material;

                if (shader.COLOR_MODULATOR != null) {
                    shader.COLOR_MODULATOR.set(material.baseColorFactor);
                }

                shader.apply();

                // --- Bind Albedo (Always Unit 0) ---
                RenderSystem.activeTexture(GL13.GL_TEXTURE0);
                RenderSystem.bindTexture(material.albedoMapGlId);

                // --- Bind Normal Map (Dynamic Unit) ---
                if (targetNormalUnit != -1 && material.normalMapGlId != -1) {
                    RenderSystem.activeTexture(GL13.GL_TEXTURE0 + targetNormalUnit);
                    RenderSystem.bindTexture(material.normalMapGlId);
                }

                // --- Bind Specular/LabPBR Map (Dynamic Unit) ---
                if (targetSpecularUnit != -1 && material.specularMapGlId != -1) {
                    RenderSystem.activeTexture(GL13.GL_TEXTURE0 + targetSpecularUnit);
                    RenderSystem.bindTexture(material.specularMapGlId);
                }

                // Reset Active Texture to 0 to ensure subsequent operations affect the primary unit
                RenderSystem.activeTexture(GL13.GL_TEXTURE0);

                GL11.glDrawArrays(GL11.GL_TRIANGLES, mesh.getFinalVertexOffset(command), command.vertexCount);
            }

            // Restore State
            GL30.glEnableVertexAttribArray(AT_UV2);
        }

        shader.clear();
    }

    /**
     * Sets up the common render state and shader uniforms used by both rendering paths.
     * This is called once per batch flush.
     *
     * @param projectionMatrix The projection matrix to upload to the shader.
     * @return The configured {@link ShaderInstance} to be used for rendering, or null if setup fails.
     */
    private ShaderInstance setupCommonRenderState(Matrix4f projectionMatrix) {
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
        if (shader.PROJECTION_MATRIX != null) shader.PROJECTION_MATRIX.set(projectionMatrix);
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
}