/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.physics;

import com.github.stephengold.joltjni.*;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Defines object and broad-phase layers for the Jolt physics simulation.
 *
 * <p>This setup follows the standard Jolt approach by separating objects into
 * moving and non-moving categories, while keeping terrain in a dedicated layer.
 * This improves broad-phase performance and keeps collision rules simple
 * and predictable.</p>
 *
 * <p>The system supports runtime configuration by pre-allocating a fixed number
 * of layer slots. New layers can be claimed and their collision rules modified
 * after initialization.</p>
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

    /**
     * Static and kinematic bodies that do not move via physics simulation
     */
    public static final short NON_MOVING = 0;

    /**
     * Dynamic bodies simulated by the physics engine
     */
    public static final short MOVING = 1;

    /**
     * Terrain geometry such as heightfields
     */
    public static final short TERRAIN = 2;

    /**
     * Maximum number of pre-allocated object layers to allow dynamic runtime additions
     */
    public static final short MAX_OBJECT_LAYERS = 64;

    /* ===================== Broad Phase Layers ===================== */

    /**
     * Broad-phase layer for non-moving objects
     */
    public static final short BP_NON_MOVING = 0;

    /**
     * Broad-phase layer for moving objects
     */
    public static final short BP_MOVING = 1;

    /**
     * Total number of broad-phase layers
     */
    public static final short NUM_BROAD_PHASE_LAYERS = 2;

    private static final AtomicInteger nextAvailableLayer = new AtomicInteger(3);

    private static BroadPhaseLayerInterfaceTable broadPhaseLayerInterface;
    private static ObjectVsBroadPhaseLayerFilter objectVsBroadPhaseLayerFilter;
    private static ObjectLayerPairFilterTable objectLayerPairFilter;

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
         * We initialize with MAX_OBJECT_LAYERS to allow dynamic configuration.
         */
        ObjectLayerPairFilterTable olpf =
                new ObjectLayerPairFilterTable(MAX_OBJECT_LAYERS);

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
                        MAX_OBJECT_LAYERS,
                        NUM_BROAD_PHASE_LAYERS
                );

        bpli.mapObjectToBroadPhaseLayer(NON_MOVING, BP_NON_MOVING);
        bpli.mapObjectToBroadPhaseLayer(TERRAIN, BP_NON_MOVING);
        bpli.mapObjectToBroadPhaseLayer(MOVING, BP_MOVING);

        // Initialize remaining slots to non-moving broad-phase by default
        for (short i = 3; i < MAX_OBJECT_LAYERS; i++) {
            bpli.mapObjectToBroadPhaseLayer(i, BP_NON_MOVING);
        }

        broadPhaseLayerInterface = bpli;

        /* -------- Object vs Broad Phase Layer Filter --------
         * Combines object-layer and broad-phase-layer filtering.
         */
        objectVsBroadPhaseLayerFilter =
                new ObjectVsBroadPhaseLayerFilterTable(
                        broadPhaseLayerInterface,
                        NUM_BROAD_PHASE_LAYERS,
                        objectLayerPairFilter,
                        MAX_OBJECT_LAYERS
                );
    }

    /**
     * Reserves a unique object layer ID from the pre-allocated pool.
     *
     * @return A new unique layer ID.
     * @throws IllegalStateException If the maximum number of layers has been exceeded.
     */
    public static short claimLayer() {
        int id = nextAvailableLayer.getAndIncrement();
        if (id >= MAX_OBJECT_LAYERS) {
            throw new IllegalStateException("Maximum pre-allocated physics layers reached: " + MAX_OBJECT_LAYERS);
        }
        return (short) id;
    }

    /**
     * Configures whether two object layers are permitted to collide.
     * This can be updated at runtime after initialization.
     *
     * @param layer1  The first object layer.
     * @param layer2  The second object layer.
     * @param enabled True if collision should be enabled, false otherwise.
     */
    public static void setCollision(short layer1, short layer2, boolean enabled) {
        if (enabled) {
            objectLayerPairFilter.enableCollision(layer1, layer2);
        } else {
            objectLayerPairFilter.disableCollision(layer1, layer2);
        }
    }

    /**
     * Assigns an object layer to a specific broad-phase layer.
     * This can be updated at runtime after initialization.
     *
     * @param objectLayer     The object layer ID.
     * @param broadPhaseLayer The broad-phase layer ID.
     */
    public static void setBroadPhaseMapping(short objectLayer, short broadPhaseLayer) {
        broadPhaseLayerInterface.mapObjectToBroadPhaseLayer(objectLayer, broadPhaseLayer);
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