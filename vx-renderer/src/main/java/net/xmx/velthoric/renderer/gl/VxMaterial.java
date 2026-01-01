/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.renderer.gl;

import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;

/**
 * Represents a PBR material defined by scalar properties and texture identifiers.
 * <p>
 * This class operates directly on OpenGL handles via LWJGL, independent of the
 * Minecraft texture system (TextureManager/DynamicTexture). It manages the
 * lifecycle of generated PBR textures (Normal and LabPBR Specular maps) created
 * from scalar factors.
 *
 * @author xI-Mx-Ix
 */
public class VxMaterial {

    /**
     * The default path for a white texture, used when no albedo map is specified.
     */
    public static final String DEFAULT_WHITE_PATH = "velthoric:renderer/white.png";

    /**
     * The unique name of the material.
     */
    public final String name;

    // --- Texture Paths (Strings instead of ResourceLocations) ---
    public String albedoPath = DEFAULT_WHITE_PATH;

    // --- OpenGL Texture IDs ---
    public int albedoMapGlId = -1;
    public int normalMapGlId = -1;
    public int specularMapGlId = -1;

    // --- Scalar Factors ---
    /**
     * The base color factor (RGBA). Defaults to white [1.0, 1.0, 1.0, 1.0].
     */
    public final float[] baseColorFactor = {1.0f, 1.0f, 1.0f, 1.0f};

    /**
     * The metallic factor (0.0 = dielectric, 1.0 = metal). Defaults to 0.0.
     */
    public float metallicFactor = 0.0f;

    /**
     * The roughness factor (0.0 = smooth, 1.0 = rough). Defaults to 1.0.
     */
    public float roughnessFactor = 1.0f;

    /**
     * Constructs a new material with the given name.
     *
     * @param name The name of the material.
     */
    public VxMaterial(String name) {
        this.name = name;
    }

    /**
     * Checks if the PBR textures (Normal and Specular) exist, and generates them
     * directly on the GPU if they do not.
     */
    public void ensureGenerated() {
        if (this.normalMapGlId == -1) {
            this.normalMapGlId = generateFlatNormalMap();
        }
        if (this.specularMapGlId == -1) {
            this.specularMapGlId = generateLabPBRSpecularMap();
        }
    }

    /**
     * Generates a 1x1 pixel flat normal map using direct OpenGL calls.
     * Color: RGB(128, 128, 255), Alpha(255).
     *
     * @return The OpenGL texture ID.
     */
    private int generateFlatNormalMap() {
        // Flat normal: X=0, Y=0, Z=1 -> mapped to 0..255 is 128, 128, 255
        return create1x1Texture((byte) 128, (byte) 128, (byte) 255, (byte) 255);
    }

    /**
     * Generates a 1x1 pixel LabPBR 1.3 compliant specular map using direct OpenGL calls.
     * <p>
     * <b>Channel Mapping:</b>
     * <ul>
     *     <li><b>Red:</b> Perceptual Smoothness (1.0 - Roughness).</li>
     *     <li><b>Green:</b> F0 (Reflectance). 230 for metals, ~10 for dielectrics.</li>
     *     <li><b>Blue:</b> Porosity (0).</li>
     *     <li><b>Alpha:</b> Emission (0).</li>
     * </ul>
     *
     * @return The OpenGL texture ID.
     */
    private int generateLabPBRSpecularMap() {
        // 1. Red: Smoothness
        float smoothness = 1.0f - this.roughnessFactor;
        byte r = (byte) (smoothness * 255.0f);

        // 2. Green: F0 (Reflectance)
        byte g;
        if (this.metallicFactor > 0.5f) {
            // Metal: LabPBR ID 230 (Generic Iron)
            g = (byte) 230;
        } else {
            // Dielectric: ~0.04 linear -> ~10/255
            g = (byte) 10;
        }

        // 3. Blue: Porosity (Default 0)
        byte b = 0;

        // 4. Alpha: Emission (Default 0)
        byte a = 0;

        return create1x1Texture(r, g, b, a);
    }

    /**
     * Creates a single-pixel 2D texture on the GPU with the specified RGBA values.
     * Uses LWJGL MemoryStack for efficient off-heap buffer management.
     *
     * @param r Red component (0-255).
     * @param g Green component (0-255).
     * @param b Blue component (0-255).
     * @param a Alpha component (0-255).
     * @return The generated OpenGL texture ID.
     */
    private int create1x1Texture(byte r, byte g, byte b, byte a) {
        int textureId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);

        // Configure texture parameters (Nearest filter is sufficient for 1x1)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);

        // Push a new stack frame to allocate a short-lived direct buffer
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer buffer = stack.malloc(4); // 4 bytes for RGBA
            buffer.put(r);
            buffer.put(g);
            buffer.put(b);
            buffer.put(a);
            buffer.flip(); // Prepare for reading

            // Upload data to the GPU
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, 1, 1, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
        }

        // Unbind texture to prevent accidental modification
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        return textureId;
    }

    /**
     * Deletes the generated OpenGL textures associated with this material.
     * Checks if IDs are valid (not -1) before deletion to prevent errors.
     */
    public void delete() {
        if (normalMapGlId != -1) {
            GL11.glDeleteTextures(normalMapGlId);
            normalMapGlId = -1;
        }
        if (specularMapGlId != -1) {
            GL11.glDeleteTextures(specularMapGlId);
            specularMapGlId = -1;
        }
        // Note: Albedo ID is typically managed by an external TextureManager and is not deleted here.
    }
}