/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.renderer;

import net.minecraft.resources.ResourceLocation;

/**
 * Represents a PBR material with properties loaded from an MTL file.
 * This includes texture maps for detailed surfaces and scalar factors for uniform properties.
 *
 * @author xI-Mx-Ix
 */
public class VxMaterial {
    /**
     * A default resource location pointing to a simple white texture.
     */
    public static final ResourceLocation DEFAULT_WHITE = ResourceLocation.tryBuild(VxRConstants.MODID, "renderer/white.png");

    /**
     * A default resource location pointing to a flat normal map texture.
     */
    public static final ResourceLocation DEFAULT_NORMAL = ResourceLocation.tryBuild(VxRConstants.MODID, "renderer/blue.png");

    /**
     * A default resource location pointing to a simple black texture.
     */
    public static final ResourceLocation DEFAULT_BLACK = ResourceLocation.tryBuild(VxRConstants.MODID, "renderer/black.png");

    /**
     * The unique name of the material, defined by the 'newmtl' keyword.
     */
    public final String name;

    // --- Texture Maps (ResourceLocations for loading) ---
    public ResourceLocation albedoMap = DEFAULT_WHITE;
    public ResourceLocation normalMap = DEFAULT_NORMAL;
    public ResourceLocation metallicMap = DEFAULT_BLACK;
    public ResourceLocation roughnessMap = DEFAULT_WHITE;

    // --- OpenGL Texture IDs (set during upload) ---
    public int albedoMapGlId = -1;
    public int normalMapGlId = -1;
    public int metallicMapGlId = -1;
    public int roughnessMapGlId = -1;

    // --- Scalar Factors ---
    /**
     * The base color factor (RGBA) applied to the model.
     * This is multiplied with the albedoMap and vertex colors. Defaults to white [1, 1, 1, 1].
     */
    public final float[] baseColorFactor = {1.0f, 1.0f, 1.0f, 1.0f};
    /**
     * The metallic factor, used when no metallicMap is provided.
     * Value ranges from 0.0 (dielectric) to 1.0 (metallic). Defaults to 0.0.
     */
    public float metallicFactor = 0.0f;
    /**
     * The roughness factor, used when no roughnessMap is provided.
     * Value ranges from 0.0 (smooth) to 1.0 (rough). Defaults to 1.0.
     */
    public float roughnessFactor = 1.0f;

    /**
     * Constructs a new material with the given name.
     * @param name The name of the material.
     */
    public VxMaterial(String name) {
        this.name = name;
    }
}