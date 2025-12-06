/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.renderer.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A generic utility for mapping logical game components (Keys) to internal model group names (Values).
 *
 * <p>This class allows the rendering logic to remain decoupled from specific game mechanics.
 * Game entities can define their own identifying Enums or constants and map them
 * to the arbitrary string names found in 3D model files.</p>
 *
 * @param <K> The type of the key used to identify parts (e.g., a VehiclePart enum).
 *
 * @author xI-Mx-Ix
 */
public class VxModelPartMapper<K> {

    private final Map<K, String> mapping = new HashMap<>();

    /**
     * Maps a logical identifier to a specific group name in the model file.
     *
     * @param key The logical identifier (e.g., MyCarParts.WHEEL_FL).
     * @param modelGroupName The exact name of the group in the .obj file (e.g., "obj_wheel_01").
     */
    public void mapGroup(K key, String modelGroupName) {
        mapping.put(key, modelGroupName);
    }

    /**
     * Retrieves the model group name associated with the given logical identifier.
     *
     * @param key The logical identifier.
     * @return An Optional containing the model group name if mapped, or empty.
     */
    public Optional<String> getGroupName(K key) {
        return Optional.ofNullable(mapping.get(key));
    }

    /**
     * Checks if a mapping exists for the given key.
     *
     * @param key The key to check.
     * @return True if a mapping exists.
     */
    public boolean hasMapping(K key) {
        return mapping.containsKey(key);
    }
}