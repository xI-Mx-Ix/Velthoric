/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.natives;

import dev.architectury.platform.Platform;
import net.xmx.velthoric.Arch;
import net.xmx.velthoric.OS;
import net.xmx.velthoric.UnsupportedOperatingSystemException;
import net.xmx.velthoric.VxNativeLibraryLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Central manager for loading all required native libraries for Velthoric.
 * This class ensures that all native libraries are loaded using a unified, robust
 * extraction and loading mechanism.
 *
 * @author xI-Mx-Ix
 */
public class VxNativeManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("Velthoric Native Manager");
    private static volatile boolean areNativesInitialized = false;

    /**
     * Initializes and loads all required native libraries.
     * This method is synchronized and idempotent, ensuring that loading only occurs once.
     * It determines a common extraction path and delegates the loading of each
     * specific library to its respective manager.
     */
    public static synchronized void initialize() {
        if (areNativesInitialized) {
            return;
        }

        LOGGER.info("Initializing Velthoric native libraries...");
        Path extractionPath = Platform.getGameFolder().resolve("velthoric").resolve("natives");

        try {
            // First, load Zstd, as it's a general-purpose utility.
            VxNativeZstd.initialize(extractionPath);

            // Second, load the Jolt physics engine.
            VxNativeJolt.initialize(extractionPath);

            areNativesInitialized = true;
            LOGGER.info("All Velthoric native libraries initialized successfully.");
        } catch (Exception e) {
            LOGGER.error("A critical error occurred while loading native libraries. Velthoric may not function correctly.", e);
            // Re-throw as a runtime exception to halt initialization.
            throw new RuntimeException("Failed to initialize Velthoric natives", e);
        }
    }

    /**
     * Loads a specific native library using a standardized pathing scheme.
     *
     * @param extractionPath The root directory to extract the library to.
     * @param libraryName The simple name of the library (e.g., "joltjni", "zstd-jni").
     * @throws UnsupportedOperatingSystemException if the current platform is not supported.
     */
    static void loadLibrary(Path extractionPath, String libraryName) {
        OS os = OS.detect();
        Arch arch = Arch.detect();

        // Ensure the current platform is one of the explicitly supported ones.
        if (os == null || arch == null) {
            throw new UnsupportedOperatingSystemException(
                    "Unsupported platform: " + System.getProperty("os.name") + " (" + System.getProperty("os.arch") + ")"
            );
        }

        // Generate the platform-specific library file name (e.g., "libjoltjni.so", "joltjni.dll").
        String libFileName = System.mapLibraryName(libraryName);

        // Construct the path to the resource inside the JAR based on the new, clean structure.
        String resourcePath = String.format("/natives/%s/%s/%s", os.folder, arch.folder, libFileName);

        LOGGER.debug("Attempting to load '{}' from resource path '{}'", libFileName, resourcePath);
        VxNativeLibraryLoader.load(extractionPath, resourcePath, libFileName);
    }

    /**
     * Checks if all native libraries have been successfully initialized.
     *
     * @return true if natives are loaded, false otherwise.
     */
    public static boolean areNativesInitialized() {
        return areNativesInitialized;
    }
}