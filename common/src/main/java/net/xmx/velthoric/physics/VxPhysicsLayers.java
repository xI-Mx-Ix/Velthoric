/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics;

import com.github.stephengold.joltjni.*;

/**
 * Defines object and broad-phase layers for the Jolt physics simulation.
 *
 * <p>This setup follows the standard Jolt approach by separating objects into
 * moving and non-moving categories, while keeping terrain in a dedicated layer.
 * This improves broad-phase performance and keeps collision rules simple
 * and predictable.</p>
 *
 * <ul>
 *   <li>NON_MOVING: Static and kinematic bodies</li>
 *   <li>MOVING: Dynamic bodies</li>
 *   <li>TERRAIN: Heightfields and large static geometry</li>
 * </ul>
 *
 * @author xI-Mx-Ix
 */
public final class VxPhysicsLayers {

    /* ===================== Object Layers ===================== */

    /** Static and kinematic bodies that do not move via physics simulation */
    public static final short NON_MOVING = 0;

    /** Dynamic bodies simulated by the physics engine */
    public static final short MOVING = 1;

    /** Terrain geometry such as heightfields */
    public static final short TERRAIN = 2;

    /** Total number of object layers */
    public static final short NUM_OBJECT_LAYERS = 3;

    /* ===================== Broad Phase Layers ===================== */

    /** Broad-phase layer for non-moving objects */
    public static final short BP_NON_MOVING = 0;

    /** Broad-phase layer for moving objects */
    public static final short BP_MOVING = 1;

    /** Total number of broad-phase layers */
    public static final short NUM_BROAD_PHASE_LAYERS = 2;

    private static BroadPhaseLayerInterface broadPhaseLayerInterface;
    private static ObjectVsBroadPhaseLayerFilter objectVsBroadPhaseLayerFilter;
    private static ObjectLayerPairFilter objectLayerPairFilter;

    /**
     * Initializes all collision filtering interfaces.
     *
     * <p>This method must be called exactly once during physics world setup.
     * It defines which object layers can collide with each other and how
     * object layers are mapped to broad-phase layers.</p>
     */
    public static void initialize() {

        /* -------- Object Layer Pair Filter --------
         * Defines which pairs of object layers are allowed to collide.
         */
        ObjectLayerPairFilterTable olpf =
                new ObjectLayerPairFilterTable(NUM_OBJECT_LAYERS);

        // Non-moving objects never collide with each other
        olpf.disableCollision(NON_MOVING, NON_MOVING);

        // Moving objects collide with everything
        olpf.enableCollision(MOVING, NON_MOVING);
        olpf.enableCollision(MOVING, MOVING);
        olpf.enableCollision(MOVING, TERRAIN);

        // Terrain only collides with moving objects
        olpf.disableCollision(TERRAIN, TERRAIN);
        olpf.disableCollision(TERRAIN, NON_MOVING);

        objectLayerPairFilter = olpf;

        /* -------- Broad Phase Layer Interface --------
         * Maps object layers to broad-phase layers.
         */
        BroadPhaseLayerInterfaceTable bpli =
                new BroadPhaseLayerInterfaceTable(
                        NUM_OBJECT_LAYERS,
                        NUM_BROAD_PHASE_LAYERS
                );

        bpli.mapObjectToBroadPhaseLayer(NON_MOVING, BP_NON_MOVING);
        bpli.mapObjectToBroadPhaseLayer(TERRAIN, BP_NON_MOVING);
        bpli.mapObjectToBroadPhaseLayer(MOVING, BP_MOVING);

        broadPhaseLayerInterface = bpli;

        /* -------- Object vs Broad Phase Layer Filter --------
         * Combines object-layer and broad-phase-layer filtering.
         */
        objectVsBroadPhaseLayerFilter =
                new ObjectVsBroadPhaseLayerFilterTable(
                        broadPhaseLayerInterface,
                        NUM_BROAD_PHASE_LAYERS,
                        objectLayerPairFilter,
                        NUM_OBJECT_LAYERS
                );
    }

    /**
     * Releases all native resources used by the layer interfaces.
     *
     * <p>This method should be called during application shutdown
     * to avoid native memory leaks.</p>
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
     * @return The configured {@link BroadPhaseLayerInterface}.
     */
    public static BroadPhaseLayerInterface getBroadPhaseLayerInterface() {
        return broadPhaseLayerInterface;
    }

    /**
     * @return The configured {@link ObjectVsBroadPhaseLayerFilter}.
     */
    public static ObjectVsBroadPhaseLayerFilter getObjectVsBroadPhaseLayerFilter() {
        return objectVsBroadPhaseLayerFilter;
    }

    /**
     * @return The configured {@link ObjectLayerPairFilter}.
     */
    public static ObjectLayerPairFilter getObjectLayerPairFilter() {
        return objectLayerPairFilter;
    }
}
