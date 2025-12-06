/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.renderer.model;

import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.renderer.VxRConstants;
import net.xmx.velthoric.renderer.mesh.IVxRenderableMesh;
import net.xmx.velthoric.renderer.mesh.VxMeshDefinition;
import net.xmx.velthoric.renderer.mesh.arena.VxArenaBuffer;
import net.xmx.velthoric.renderer.mesh.impl.VxArenaMesh;
import net.xmx.velthoric.renderer.mesh.impl.VxDedicatedMesh;
import net.xmx.velthoric.renderer.model.parser.VxObjParser;
import net.xmx.velthoric.renderer.model.raw.VxRawModel;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A unified manager for loading, processing, and caching 3D models.
 * <p>
 * This class acts as the central hub for the entire model pipeline. It implements a multi-tiered
 * caching strategy to minimize disk I/O, CPU processing, and GPU memory allocation.
 * </p>
 * <h3>Pipeline Stages:</h3>
 * <ol>
 *     <li><b>Raw Model (CPU - Editable):</b> The {@link VxRawModel} represents the parsed data from disk.
 *     It maps directly to the file structure (OBJ groups, faces) and allows for logic-based manipulation
 *     before rendering preparation.</li>
 *
 *     <li><b>Mesh Definition (CPU - Baked):</b> The {@link VxMeshDefinition} is the result of "baking" a raw model.
 *     This stage flattens the geometry into an interleaved buffer, calculates tangents, and generates
 *     render commands. It is optimized for upload but still resides in system RAM.</li>
 *
 *     <li><b>GPU Mesh (GPU - Renderable):</b> These are the final handles ({@link VxDedicatedMesh} or {@link VxArenaMesh})
 *     that point to actual OpenGL resources (VBOs/VAOs).</li>
 * </ol>
 *
 * @author xI-Mx-Ix
 */
public final class VxModelManager {

    /**
     * Cache for raw, structural model data.
     * This cache is checked first to avoid re-reading and re-parsing OBJ files from disk.
     */
    private static final Map<ResourceLocation, VxRawModel> RAW_CACHE = new HashMap<>();

    /**
     * Cache for baked mesh definitions.
     * This cache stores the intermediate byte buffers before they are uploaded to the GPU.
     * It prevents re-calculating normals/tangents and re-interleaving data for models already processed.
     */
    private static final Map<ResourceLocation, VxMeshDefinition> DEFINITION_CACHE = new HashMap<>();

    /**
     * Cache for standalone, dedicated GPU meshes.
     * These meshes own their own VBOs and are typically used for large or unique models.
     */
    private static final Map<ResourceLocation, VxDedicatedMesh> STANDALONE_MESH_CACHE = new HashMap<>();

    /**
     * Cache for sub-meshes allocated within the shared arena buffer.
     * These are lightweight handles used for rendering many small objects efficiently.
     */
    private static final Map<ResourceLocation, VxArenaMesh> ARENA_SUB_MESH_CACHE = new HashMap<>();

    /**
     * Private constructor to prevent instantiation of this static utility class.
     */
    private VxModelManager() {}

    /**
     * Retrieves the raw, editable model data for a given resource.
     * <p>
     * If the model is not in the cache, it will attempt to load and parse it from the file system.
     * This method is useful for game logic that needs to inspect model structure (e.g., getting the
     * position of a specific named group) without necessarily rendering it.
     *
     * @param location The resource location of the OBJ file.
     * @return An {@link Optional} containing the raw model data, or empty if loading failed.
     */
    public static Optional<VxRawModel> getRawModel(ResourceLocation location) {
        // 1. Check the L1 cache (Raw Data)
        VxRawModel cached = RAW_CACHE.get(location);
        if (cached != null) {
            return Optional.of(cached);
        }

        // 2. Cache miss: Parse from disk
        try {
            VxRawModel raw = VxObjParser.parse(location);
            RAW_CACHE.put(location, raw);
            return Optional.of(raw);
        } catch (IOException e) {
            VxRConstants.LOGGER.error("Failed to parse raw model from disk: {}", location, e);
            return Optional.empty();
        }
    }

    /**
     * Retrieves the GPU-ready mesh definition for a given resource.
     * <p>
     * This method automatically handles the dependency chain:
     * <ul>
     *     <li>If the definition is cached, it returns it immediately.</li>
     *     <li>If not, it requests the {@link VxRawModel} (triggering a load if necessary).</li>
     *     <li>Once the raw model is obtained, it "bakes" it via {@link VxModelBaker} to produce the definition.</li>
     * </ul>
     *
     * @param location The resource location of the OBJ file.
     * @return An {@link Optional} containing the baked mesh definition, or empty if the process failed.
     */
    public static Optional<VxMeshDefinition> getDefinition(ResourceLocation location) {
        // 1. Check the L2 cache (Baked Definitions)
        VxMeshDefinition cached = DEFINITION_CACHE.get(location);
        if (cached != null) {
            return Optional.of(cached);
        }

        // 2. Cache miss: Obtain raw data to bake
        Optional<VxRawModel> rawOpt = getRawModel(location);
        if (rawOpt.isPresent()) {
            // 3. Bake the raw data into a GPU-compatible format (calculating tangents, etc.)
            VxMeshDefinition baked = VxModelBaker.bake(rawOpt.get());
            DEFINITION_CACHE.put(location, baked);
            return Optional.of(baked);
        }

        // 4. Failed to obtain raw data
        return Optional.empty();
    }

    /**
     * Retrieves or creates a standalone mesh (dedicated VBO) for the given resource.
     * <p>
     * Use this for complex models that are rendered individually or have specific isolation requirements.
     *
     * @param location The resource location of the model.
     * @return An {@link Optional} containing the dedicated mesh handle.
     */
    public static Optional<VxDedicatedMesh> getStandaloneMesh(ResourceLocation location) {
        // 1. Check the L3 cache (GPU Objects)
        VxDedicatedMesh cached = STANDALONE_MESH_CACHE.get(location);
        if (cached != null) {
            return Optional.of(cached);
        }

        // 2. Cache miss: Obtain the baked definition
        Optional<VxMeshDefinition> definitionOpt = getDefinition(location);
        if (definitionOpt.isPresent()) {
            // 3. Upload to a dedicated VBO and cache the result
            VxDedicatedMesh gpuMesh = new VxDedicatedMesh(definitionOpt.get());
            STANDALONE_MESH_CACHE.put(location, gpuMesh);
            return Optional.of(gpuMesh);
        }

        // 4. Failed to load definition
        VxRConstants.LOGGER.error("Failed to create standalone mesh for {}: definition not found.", location);
        return Optional.empty();
    }

    /**
     * Retrieves or creates a sub-mesh allocated within the shared {@link VxArenaBuffer}.
     * <p>
     * Use this for smaller, numerous models to reduce draw call overhead and state changes.
     * The manager handles allocation within the arena automatically.
     *
     * @param location The resource location of the model.
     * @return An {@link Optional} containing the arena mesh handle.
     */
    public static Optional<VxArenaMesh> getArenaMesh(ResourceLocation location) {
        // 1. Check the L3 cache (GPU Objects)
        VxArenaMesh cached = ARENA_SUB_MESH_CACHE.get(location);
        if (cached != null) {
            return Optional.of(cached);
        }

        // 2. Cache miss: Obtain the baked definition
        Optional<VxMeshDefinition> definitionOpt = getDefinition(location);
        if (definitionOpt.isPresent()) {
            // 3. Allocate space in the shared Arena Buffer
            VxArenaMesh subMesh = VxArenaBuffer.getInstance().allocate(definitionOpt.get());

            if (subMesh != null) {
                ARENA_SUB_MESH_CACHE.put(location, subMesh);
                return Optional.of(subMesh);
            } else {
                VxRConstants.LOGGER.error("Failed to allocate arena buffer space for model: {}", location);
            }
        }

        // 4. Failed to load definition or allocate memory
        return Optional.empty();
    }

    /**
     * Clears all caches and releases all associated GPU resources.
     * <p>
     * This method is destructive and should typically be called during a full resource reload (F3+T)
     * or when the application is shutting down. It ensures that:
     * <ul>
     *     <li>All CPU-side data (Raw models, Definitions) is discarded.</li>
     *     <li>All dedicated VBOs/VAOs are deleted via OpenGL.</li>
     *     <li>The shared Arena Buffer is destroyed and reset.</li>
     * </ul>
     */
    public static void clear() {
        VxRConstants.LOGGER.info("Clearing Velthoric model caches and GPU resources...");

        // 1. Clear CPU-side raw data and definitions
        RAW_CACHE.clear();
        DEFINITION_CACHE.clear();

        // 2. Delete all standalone GPU meshes and clear the map
        STANDALONE_MESH_CACHE.values().forEach(IVxRenderableMesh::delete);
        STANDALONE_MESH_CACHE.clear();

        // 3. Delete all arena sub-mesh handles (logic-wise deletion)
        ARENA_SUB_MESH_CACHE.values().forEach(VxArenaMesh::delete);
        ARENA_SUB_MESH_CACHE.clear();

        // 4. Destroy the global arena buffer (physically freeing the shared VBO)
        VxArenaBuffer.getInstance().delete();

        VxRConstants.LOGGER.info("Velthoric model manager cleared successfully.");
    }
}