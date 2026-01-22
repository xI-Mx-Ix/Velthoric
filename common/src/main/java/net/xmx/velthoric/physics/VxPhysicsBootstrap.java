/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics;

import com.github.stephengold.joltjni.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bootstraps the Jolt Physics engine.
 * <p>
 * This class handles the initialization of Jolt factories, memory allocators,
 * callbacks, and physics layers. It assumes that the native library has already
 * been loaded via {@link net.xmx.velthoric.natives.systems.NativeJolt}.
 *
 * @author xI-Mx-Ix
 */
public class VxPhysicsBootstrap {

    private static final Logger LOGGER = LoggerFactory.getLogger("Velthoric Physics Bootstrap");
    private static volatile boolean isInitialized = false;

    /**
     * Initializes the Jolt physics engine components.
     * <p>
     * This registers the default allocators, callbacks, and initializes the
     * factory and physics layers.
     *
     * @throws IllegalStateException if the Jolt Factory cannot be created.
     */
    public static void initialize() {
        if (isInitialized) {
            return;
        }

        LOGGER.debug("Initializing Jolt Physics engine components...");

        // Register standard Jolt components
        Jolt.registerDefaultAllocator();
        Jolt.installDefaultAssertCallback();
        Jolt.installDefaultTraceCallback();
        JoltPhysicsObject.startCleaner();

        // Initialize the Factory
        if (!Jolt.newFactory()) {
            throw new IllegalStateException("Jolt Factory could not be created.");
        }

        // Register types and initialize custom layers
        Jolt.registerTypes();
        VxPhysicsLayers.initialize();

        isInitialized = true;
        LOGGER.debug("Jolt Physics engine initialized successfully.");
    }

    /**
     * Shuts down the Jolt physics system.
     * This method cleans up all allocated Jolt resources and destroys the factory.
     */
    public static void shutdown() {
        if (!isInitialized) {
            return;
        }
        LOGGER.debug("Shutting down Jolt Physics engine...");
        VxPhysicsLayers.shutdown();
        Jolt.destroyFactory();
        isInitialized = false;
        LOGGER.debug("Jolt Physics engine shutdown complete.");
    }

    /**
     * Checks if the physics engine has been initialized.
     *
     * @return true if initialized, false otherwise.
     */
    public static boolean isInitialized() {
        return isInitialized;
    }

    /**
     * Gets the configured broad-phase layer interface.
     *
     * @return The singleton instance of BroadPhaseLayerInterface.
     */
    public static BroadPhaseLayerInterface getBroadPhaseLayerInterface() {
        return VxPhysicsLayers.getBroadPhaseLayerInterface();
    }

    /**
     * Gets the configured object vs. broad-phase layer filter.
     *
     * @return The singleton instance of ObjectVsBroadPhaseLayerFilter.
     */
    public static ObjectVsBroadPhaseLayerFilter getObjectVsBroadPhaseLayerFilter() {
        return VxPhysicsLayers.getObjectVsBroadPhaseLayerFilter();
    }

    /**
     * Gets the configured object layer pair filter.
     *
     * @return The singleton instance of ObjectLayerPairFilter.
     */
    public static ObjectLayerPairFilter getObjectLayerPairFilter() {
        return VxPhysicsLayers.getObjectLayerPairFilter();
    }
}