/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.physics.world;

import com.github.stephengold.joltjni.*;

/**
 * Defines the object layers for the Jolt physics simulation and manages the collision filtering logic.
 * Layers are used to efficiently determine which types of objects can collide with each other.
 * This class is responsible for creating and configuring the interfaces that govern these interactions.
 *
 * @author xI-Mx-Ix
 */
public class VxLayers {
    public static final short STATIC = 0;
    public static final short DYNAMIC = 1;
    public static final short KINEMATIC = 2;
    public static final short TERRAIN = 3;
    public static final short NUM_LAYERS = 4;

    private static BroadPhaseLayerInterface broadPhaseLayerInterface;
    private static ObjectVsBroadPhaseLayerFilter objectVsBroadPhaseLayerFilter;
    private static ObjectLayerPairFilter objectLayerPairFilter;

    /**
     * Initializes the collision filtering interfaces for the physics world.
     * This method sets up the rules for how different object layers interact and collide.
     * It should only be called once during the application startup.
     */
    public static void initialize() {
        final int numObjectLayers = NUM_LAYERS;
        final int numBroadPhaseLayers = NUM_LAYERS;

        // --- Object Layer Pair Filter ---
        // This filter defines which pairs of object layers can collide with each other.
        ObjectLayerPairFilterTable olpfTable = new ObjectLayerPairFilterTable(numObjectLayers);

        olpfTable.disableCollision(STATIC, STATIC);
        olpfTable.disableCollision(STATIC, TERRAIN);
        olpfTable.enableCollision(STATIC, DYNAMIC);
        olpfTable.enableCollision(STATIC, KINEMATIC);

        olpfTable.enableCollision(DYNAMIC, DYNAMIC);
        olpfTable.enableCollision(DYNAMIC, KINEMATIC);
        olpfTable.enableCollision(DYNAMIC, TERRAIN);

        olpfTable.enableCollision(KINEMATIC, KINEMATIC);
        olpfTable.enableCollision(KINEMATIC, DYNAMIC);
        olpfTable.enableCollision(KINEMATIC, TERRAIN);

        olpfTable.enableCollision(TERRAIN, DYNAMIC);
        olpfTable.enableCollision(TERRAIN, KINEMATIC);
        objectLayerPairFilter = olpfTable;

        // --- Broad Phase Layer Interface ---
        // This interface maps object layers to broad-phase layers.
        // In this setup, there is a one-to-one mapping.
        BroadPhaseLayerInterfaceTable bpliTable = new BroadPhaseLayerInterfaceTable(numObjectLayers, numBroadPhaseLayers);
        bpliTable.mapObjectToBroadPhaseLayer(STATIC, STATIC);
        bpliTable.mapObjectToBroadPhaseLayer(DYNAMIC, DYNAMIC);
        bpliTable.mapObjectToBroadPhaseLayer(KINEMATIC, KINEMATIC);
        bpliTable.mapObjectToBroadPhaseLayer(TERRAIN, TERRAIN);
        broadPhaseLayerInterface = bpliTable;

        // --- Object vs Broad Phase Layer Filter ---
        // This filter combines the previous two to determine if an object from a certain
        // object layer should collide with a broad-phase layer.
        objectVsBroadPhaseLayerFilter = new ObjectVsBroadPhaseLayerFilterTable(
                broadPhaseLayerInterface, numBroadPhaseLayers,
                objectLayerPairFilter, numObjectLayers
        );
    }

    /**
     * Cleans up and releases the native resources used by the layer interfaces.
     * This should be called during application shutdown.
     */
    public static void shutdown() {
        if (objectVsBroadPhaseLayerFilter != null) {
            objectVsBroadPhaseLayerFilter.close();
            objectVsBroadPhaseLayerFilter = null;
        }
        if (objectLayerPairFilter != null) {
            objectLayerPairFilter.close();
            objectLayerPairFilter = null;
        }
        if (broadPhaseLayerInterface != null) {
            broadPhaseLayerInterface.close();
            broadPhaseLayerInterface = null;
        }
    }

    /**
     * Gets the configured broad-phase layer interface.
     *
     * @return The singleton instance of BroadPhaseLayerInterface.
     */
    public static BroadPhaseLayerInterface getBroadPhaseLayerInterface() {
        return broadPhaseLayerInterface;
    }

    /**
     * Gets the configured object vs. broad-phase layer filter.
     *
     * @return The singleton instance of ObjectVsBroadPhaseLayerFilter.
     */
    public static ObjectVsBroadPhaseLayerFilter getObjectVsBroadPhaseLayerFilter() {
        return objectVsBroadPhaseLayerFilter;
    }

    /**
     * Gets the configured object layer pair filter.
     *
     * @return The singleton instance of ObjectLayerPairFilter.
     */
    public static ObjectLayerPairFilter getObjectLayerPairFilter() {
        return objectLayerPairFilter;
    }
}