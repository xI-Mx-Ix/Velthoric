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
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.vxnative.Arch;
import net.xmx.vxnative.OS;
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
     *
     * @param extractionPath The root directory where native libraries should be extracted.
     * @throws IllegalStateException if the Jolt Factory cannot be created.
     */
    public static void initialize(Path extractionPath) {
        if (isInitialized) {
            return;
        }

        VxMainClass.LOGGER.debug("Performing Jolt Physics initialization...");

        String resourcePath = getNativeLibraryResourcePath();
        if (resourcePath == null) {
            throw new UnsupportedOperationException("Unsupported platform for JoltJNI: " +
                    System.getProperty("os.name") + " (" + System.getProperty("os.arch") + ")");
        }

        String libFileName = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);

        VxNativeLibraryLoader.load(extractionPath, resourcePath, libFileName);

        Jolt.registerDefaultAllocator();
        Jolt.installDefaultAssertCallback();
        Jolt.installDefaultTraceCallback();
        JoltPhysicsObject.startCleaner();

        if (!Jolt.newFactory()) {
            throw new IllegalStateException("Jolt Factory could not be created.");
        }
        Jolt.registerTypes();
        VxLayers.initialize();

        isInitialized = true;
        VxMainClass.LOGGER.debug("Jolt Physics initialization complete.");
    }

    private static String getNativeLibraryResourcePath() {
        OS os = OS.detect();
        Arch arch = Arch.detect();

        if (os == null || arch == null) return null;

        String libName = System.mapLibraryName("joltjni");
        return String.format("/%s/%s/com/github/stephengold/%s", os.folder, arch.folder, libName);
    }

    /**
     * Shuts down the Jolt physics system.
     * This method cleans up all allocated resources.
     */
    public static void shutdown() {
        if (!isInitialized) {
            return;
        }
        VxMainClass.LOGGER.debug("Performing Physics shutdown...");
        VxLayers.shutdown();
        Jolt.destroyFactory();
        isInitialized = false;
        VxMainClass.LOGGER.debug("Physics shutdown complete.");
    }

    /**
     * Checks if the Jolt physics system has been initialized.
     * @return true if initialized, false otherwise.
     */
    public static boolean isInitialized() {
        return isInitialized;
    }

    /**
     * Gets the configured broad-phase layer interface.
     * @return The singleton instance of BroadPhaseLayerInterface.
     */
    public static BroadPhaseLayerInterface getBroadPhaseLayerInterface() {
        return VxLayers.getBroadPhaseLayerInterface();
    }

    /**
     * Gets the configured object vs. broad-phase layer filter.
     * @return The singleton instance of ObjectVsBroadPhaseLayerFilter.
     */
    public static ObjectVsBroadPhaseLayerFilter getObjectVsBroadPhaseLayerFilter() {
        return VxLayers.getObjectVsBroadPhaseLayerFilter();
    }

    /**
     * Gets the configured object layer pair filter.
     * @return The singleton instance of ObjectLayerPairFilter.
     */
    public static ObjectLayerPairFilter getObjectLayerPairFilter() {
        return VxLayers.getObjectLayerPairFilter();
    }
}