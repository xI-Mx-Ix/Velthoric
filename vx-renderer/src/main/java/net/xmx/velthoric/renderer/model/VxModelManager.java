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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A unified manager for loading and caching all types of models.
 * This class handles CPU-side mesh definitions, GPU-side standalone meshes,
 * and GPU-side arena-allocated sub-meshes to prevent redundant parsing and uploading.
 * It serves as the single point of contact for all model-related resource management.
 *
 * @author xI-Mx-Ix
 */
public final class VxModelManager {

    /**
     * Caches CPU-side mesh definitions to avoid re-parsing model files.
     */
    private static final Map<ResourceLocation, VxMeshDefinition> DEFINITION_CACHE = new HashMap<>();

    /**
     * Caches GPU-side standalone meshes, each with its own VBO/VAO.
     */
    private static final Map<ResourceLocation, VxDedicatedMesh> STANDALONE_MESH_CACHE = new HashMap<>();

    /**
     * Caches handles to sub-meshes allocated within the global arena buffer.
     */
    private static final Map<ResourceLocation, VxArenaMesh> ARENA_SUB_MESH_CACHE = new HashMap<>();

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private VxModelManager() {}

    /**
     * Retrieves a cached standalone mesh, or creates and caches a new one if not present.
     * Standalone meshes are self-contained and manage their own GPU resources.
     *
     * @param location The resource location of the model file.
     * @return An Optional containing the {@link VxDedicatedMesh}, or empty if loading fails.
     */
    public static Optional<VxDedicatedMesh> getStandaloneMesh(ResourceLocation location) {
        VxDedicatedMesh cached = STANDALONE_MESH_CACHE.get(location);
        if (cached != null) {
            return Optional.of(cached);
        }

        Optional<VxMeshDefinition> definitionOpt = getDefinition(location);
        if (definitionOpt.isPresent()) {
            VxDedicatedMesh gpuMesh = new VxDedicatedMesh(definitionOpt.get());
            STANDALONE_MESH_CACHE.put(location, gpuMesh);
            return Optional.of(gpuMesh);
        } else {
            VxRConstants.LOGGER.error("Failed to create standalone mesh because definition failed to load: {}", location);
            return Optional.empty();
        }
    }

    /**
     * Retrieves a cached arena sub-mesh, or allocates and caches a new one if not present.
     * Arena sub-meshes are lightweight handles to data stored in a large, shared GPU buffer,
     * which is more efficient for rendering many small models.
     *
     * @param location The resource location of the model file.
     * @return An Optional containing the {@link VxArenaMesh} handle, or empty if loading or allocation fails.
     */
    public static Optional<VxArenaMesh> getArenaMesh(ResourceLocation location) {
        VxArenaMesh cached = ARENA_SUB_MESH_CACHE.get(location);
        if (cached != null) {
            return Optional.of(cached);
        }

        Optional<VxMeshDefinition> definitionOpt = getDefinition(location);
        if (definitionOpt.isPresent()) {
            VxArenaMesh subMesh = VxArenaBuffer.getInstance().allocate(definitionOpt.get());
            if (subMesh != null) {
                ARENA_SUB_MESH_CACHE.put(location, subMesh);
                return Optional.of(subMesh);
            } else {
                VxRConstants.LOGGER.error("Failed to allocate arena sub-mesh for: {}", location);
                return Optional.empty();
            }
        } else {
            VxRConstants.LOGGER.error("Failed to create arena sub-mesh because definition failed to load: {}", location);
            return Optional.empty();
        }
    }

    /**
     * Retrieves a cached mesh definition from memory, or parses it from a file if not present.
     * This is the internal source of model data for creating GPU-resident meshes.
     *
     * @param location The resource location of the model file.
     * @return An Optional containing the {@link VxMeshDefinition}, or empty if parsing fails.
     */
    private static Optional<VxMeshDefinition> getDefinition(ResourceLocation location) {
        VxMeshDefinition cached = DEFINITION_CACHE.get(location);
        if (cached != null) {
            return Optional.of(cached);
        }

        try {
            VxMeshDefinition definition = VxObjParser.parse(location);
            DEFINITION_CACHE.put(location, definition);
            return Optional.of(definition);
        } catch (IOException e) {
            VxRConstants.LOGGER.error("Failed to parse model definition: {}", location, e);
            return Optional.empty();
        }
    }

    /**
     * Clears all model-related caches and frees all associated GPU resources.
     * This includes CPU-side definitions, standalone meshes, and the entire arena buffer.
     * This should be called on resource reload to prevent memory leaks and load new model versions.
     */
    public static void clear() {
        // 1. Clear CPU-side data cache.
        DEFINITION_CACHE.clear();

        // 2. Delete all standalone GPU meshes and clear their cache.
        STANDALONE_MESH_CACHE.values().forEach(IVxRenderableMesh::delete);
        STANDALONE_MESH_CACHE.clear();

        // 3. Free all arena sub-meshes from the global buffer and clear their cache.
        ARENA_SUB_MESH_CACHE.values().forEach(VxArenaMesh::delete);
        ARENA_SUB_MESH_CACHE.clear();

        // 4. Finally, delete the global arena buffer itself.
        VxArenaBuffer.getInstance().delete();
    }
}