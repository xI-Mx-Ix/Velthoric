/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.natives;

import com.github.luben.zstd.util.Native;
import net.xmx.velthoric.UnsupportedOperatingSystemException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Manages the lifecycle of the Zstd-JNI native library.
 * This class hijacks the default loading mechanism of zstd-jni to use
 * Velthoric's unified native loader, ensuring consistent extraction and loading.
 *
 * @author xI-Mx-Ix
 */
public class VxNativeZstd {

    private static final Logger LOGGER = LoggerFactory.getLogger("Velthoric Zstd-JNI");
    private static volatile boolean isInitialized = false;

    /**
     * Initializes the Zstd-JNI native library using the custom loader.
     * It prevents the default zstd-jni loader from running and then delegates
     * the loading process to the central VxNativeManager.
     *
     * @param extractionPath The root directory where native libraries should be extracted.
     * @throws UnsupportedOperatingSystemException if the current platform is not supported.
     */
    public static void initialize(Path extractionPath) {
        if (isInitialized) {
            return;
        }

        LOGGER.debug("Performing Zstd-JNI initialization...");

        // Prevent zstd-jni from attempting its own loading mechanism.
        // This is a critical first step and must be called before any other Zstd class is touched.
        Native.assumeLoaded();

        // Delegate the platform detection and loading to the central manager.
        VxNativeManager.loadLibrary(extractionPath, "zstd-jni");

        isInitialized = true;
        LOGGER.debug("Zstd-JNI native library loaded successfully via Velthoric loader.");
    }

    /**
     * Checks if the Zstd-JNI native library has been initialized.
     *
     * @return true if initialized, false otherwise.
     */
    public static boolean isInitialized() {
        return isInitialized;
    }
}