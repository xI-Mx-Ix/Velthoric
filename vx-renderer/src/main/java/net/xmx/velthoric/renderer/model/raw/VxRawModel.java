/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.renderer.model.raw;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.xmx.velthoric.renderer.gl.VxMaterial;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents the raw, editable data of a 3D model in CPU memory.
 * <p>
 * Unlike the final MeshDefinition, this class exposes the structure of the model (vertices,
 * faces, groups) in a mutable way. This allows for logic-based manipulation or analysis
 * of the model before it is "baked" into a GPU-ready format.
 *
 * @author xI-Mx-Ix
 */
public class VxRawModel {

    // --- Global Data Pools ---
    // All groups share these pools to save memory and handle shared vertices.
    public final FloatArrayList positions = new FloatArrayList();
    public final FloatArrayList texCoords = new FloatArrayList();
    public final FloatArrayList normals = new FloatArrayList();
    public final FloatArrayList colors = new FloatArrayList();

    /**
     * The structural grouping of the model.
     * Key: Group Name (e.g., "body", "wheel").
     */
    public final Map<String, VxRawGroup> groups = new LinkedHashMap<>();

    /**
     * A registry of materials used by this model.
     */
    public final Map<String, VxMaterial> materials = new LinkedHashMap<>();

    /**
     * Creates a new empty raw model.
     */
    public VxRawModel() {
        // Default white color fallback
        colors.add(1.0f); colors.add(1.0f); colors.add(1.0f);
    }

    /**
     * Gets or creates a group by name.
     * @param name The name of the group.
     * @return The raw group instance.
     */
    public VxRawGroup getGroup(String name) {
        return groups.computeIfAbsent(name, VxRawGroup::new);
    }

    /**
     * Calculates the total number of vertices required if this model were to be triangulated and flattened.
     * Useful for buffer allocation.
     *
     * @return The vertex count.
     */
    public int calculateTotalVertexCount() {
        int count = 0;
        for (VxRawGroup group : groups.values()) {
            for (VxRawMesh mesh : group.meshesByMaterial.values()) {
                // Each face has 3 vertices
                count += mesh.getFaceCount() * 3;
            }
        }
        return count;
    }
}