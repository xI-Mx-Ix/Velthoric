/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.natives;

import com.github.stephengold.joltjni.Jolt;
import com.github.stephengold.joltjni.JoltPhysicsObject;
import com.github.stephengold.joltjni.BroadPhaseLayerInterface;
import com.github.stephengold.joltjni.ObjectLayerPairFilter;
import com.github.stephengold.joltjni.ObjectVsBroadPhaseLayerFilter;
import dev.architectury.platform.Platform;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.vxnative.VxNativeLibraryLoader;

import java.nio.file.Path;

/**
 * Manages the lifecycle of the Jolt physics native library.
 * This includes loading the native library, initializing the Jolt factory,
 * setting up allocators and callbacks, and managing the physics layer interfaces.
 *
 * @author xI-Mx-Ix
 */
public class VxNativeJolt {

    private static volatile boolean isInitialized = false;

    /**
     * Initializes the Jolt physics system.
     * This method loads the native library, sets up the necessary Jolt components,
     * registers default callbacks, and initializes the collision layer logic.
     * It ensures that initialization only occurs once.
     *
     * @throws IllegalStateException if the Jolt Factory cannot be created.
     */
    public static void initialize() {
        if (isInitialized) {
            return;
        }

        VxMainClass.LOGGER.debug("Performing Physics initialization...");

        Path extractionPath = Platform.getGameFolder().resolve("velthoric").resolve("natives");
        VxNativeLibraryLoader.load(extractionPath);

        Jolt.registerDefaultAllocator();
        Jolt.installDefaultAssertCallback();
        Jolt.installDefaultTraceCallback();
        JoltPhysicsObject.startCleaner();

        if (!Jolt.newFactory()) {
            throw new IllegalStateException("Jolt Factory could not be created.");
        }
        Jolt.registerTypes();

        // Delegate the creation and configuration of collision filters to the VxLayers class.
        VxLayers.initialize();

        isInitialized = true;
        VxMainClass.LOGGER.debug("Physics initialization complete.");
    }

    /**
     * Shuts down the Jolt physics system.
     * This method cleans up all allocated resources, including layer interfaces and the Jolt factory.
     * It ensures that shutdown only occurs if the system was previously initialized.
     */
    public static void shutdown() {
        if (!isInitialized) {
            return;
        }

        VxMainClass.LOGGER.debug("Performing Physics shutdown...");

        // Delegate the cleanup of layer interfaces to the VxLayers class.
        VxLayers.shutdown();

        Jolt.destroyFactory();
        isInitialized = false;
        VxMainClass.LOGGER.debug("Physics shutdown complete.");
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
        return VxLayers.getBroadPhaseLayerInterface();
    }

    /**
     * Gets the configured object vs. broad-phase layer filter.
     *
     * @return The singleton instance of ObjectVsBroadPhaseLayerFilter.
     */
    public static ObjectVsBroadPhaseLayerFilter getObjectVsBroadPhaseLayerFilter() {
        return VxLayers.getObjectVsBroadPhaseLayerFilter();
    }

    /**
     * Gets the configured object layer pair filter.
     *
     * @return The singleton instance of ObjectLayerPairFilter.
     */
    public static ObjectLayerPairFilter getObjectLayerPairFilter() {
        return VxLayers.getObjectLayerPairFilter();
    }
}