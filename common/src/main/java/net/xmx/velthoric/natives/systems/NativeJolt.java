/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.natives.systems;

import net.xmx.velthoric.natives.os.UnsupportedOperatingSystemException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Manages the lifecycle of the Jolt physics native library.
 * This class orchestrates the loading of the raw native library by delegating
 * the core loading process to the central NativeManager.
 * <p>
 * It does not initialize the Jolt engine or factories; that logic is handled
 * by the physics bootstrap.
 *
 * @author xI-Mx-Ix
 */
public class NativeJolt {

    private static final Logger LOGGER = LoggerFactory.getLogger("Velthoric JoltJNI");
    private static volatile boolean isInitialized = false;

    /**
     * Initializes the Jolt physics native library.
     * This method delegates the platform detection and loading to the central manager.
     *
     * @param extractionPath The root directory where native libraries should be extracted.
     * @throws UnsupportedOperatingSystemException if the current platform is not supported.
     */
    public static void initialize(Path extractionPath) {
        if (isInitialized) {
            return;
        }

        LOGGER.debug("Performing JoltJNI native loading...");

        // Delegate the platform detection and loading to the central manager.
        NativeManager.loadLibrary(extractionPath, "joltjni");

        isInitialized = true;
        LOGGER.debug("JoltJNI native library loaded successfully via Velthoric loader.");
    }

    /**
     * Checks if the Jolt physics native library has been loaded.
     *
     * @return true if loaded, false otherwise.
     */
    public static boolean isInitialized() {
        return isInitialized;
    }
}