/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.natives;

import com.github.stephengold.joltjni.BroadPhaseLayerInterface;
import com.github.stephengold.joltjni.Jolt;
import com.github.stephengold.joltjni.JoltPhysicsObject;
import com.github.stephengold.joltjni.ObjectLayerPairFilter;
import com.github.stephengold.joltjni.ObjectVsBroadPhaseLayerFilter;
import net.xmx.velthoric.physics.VxPhysicsLayers;
import net.xmx.vxnative.UnsupportedOperatingSystemException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Manages the lifecycle of the Jolt physics native library.
 * This class orchestrates the loading and initialization of Jolt-specific components
 * by delegating the core loading process to a central manager.
 *
 * @author xI-Mx-Ix
 */
public class VxNativeJolt {

    private static final Logger LOGGER = LoggerFactory.getLogger("Velthoric JoltJNI");
    private static volatile boolean isInitialized = false;

    /**
     * Initializes the Jolt physics system.
     * This method delegates the native library loading to the central VxNativeManager,
     * then proceeds with Jolt-specific setup like registering allocators and callbacks.
     *
     * @param extractionPath The root directory where native libraries should be extracted.
     * @throws IllegalStateException if the Jolt Factory cannot be created.
     * @throws UnsupportedOperatingSystemException if the current platform is not supported.
     */
    public static void initialize(Path extractionPath) {
        if (isInitialized) {
            return;
        }

        LOGGER.debug("Performing JoltJNI initialization...");

        // Delegate the platform detection and loading to the central manager.
        VxNativeManager.loadLibrary(extractionPath, "joltjni");

        // Proceed with Jolt-specific initialization now that the native library is loaded.
        Jolt.registerDefaultAllocator();
        Jolt.installDefaultAssertCallback();
        Jolt.installDefaultTraceCallback();
        JoltPhysicsObject.startCleaner();

        if (!Jolt.newFactory()) {
            throw new IllegalStateException("Jolt Factory could not be created.");
        }
        Jolt.registerTypes();
        VxPhysicsLayers.initialize(); // Assuming VxPhysicsLayers is another class for layer setup.

        isInitialized = true;
        LOGGER.debug("JoltJNI native library loaded and initialized successfully via Velthoric loader.");
    }

    /**
     * Shuts down the Jolt physics system.
     * This method cleans up all allocated Jolt resources.
     */
    public static void shutdown() {
        if (!isInitialized) {
            return;
        }
        LOGGER.debug("Performing JoltJNI shutdown...");
        VxPhysicsLayers.shutdown();
        Jolt.destroyFactory();
        isInitialized = false;
        LOGGER.debug("JoltJNI shutdown complete.");
    }

    /**
     * Checks if the Jolt physics system has been initialized.
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