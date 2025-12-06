/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.renderer.model.raw;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a named group of geometry within a raw model.
 * It acts as a container for {@link VxRawMesh} instances, separated by material.
 *
 * @author xI-Mx-Ix
 */
public class VxRawGroup {
    public final String name;

    /**
     * Map of Material Name -> Raw Mesh Data.
     * This separation ensures we can create efficient draw calls per material.
     */
    public final Map<String, VxRawMesh> meshesByMaterial = new LinkedHashMap<>();

    public VxRawGroup(String name) {
        this.name = name;
    }

    /**
     * Retrieves or creates the raw mesh for a specific material within this group.
     *
     * @param materialName The name of the material.
     * @return The raw mesh container.
     */
    public VxRawMesh getMesh(String materialName) {
        return meshesByMaterial.computeIfAbsent(materialName, k -> new VxRawMesh());
    }
}