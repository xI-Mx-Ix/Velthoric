/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.natives;

import dev.architectury.platform.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Central manager for loading all required native libraries for Velthoric.
 * This class ensures that both Jolt and Zstd native libraries are loaded
 * using a unified, robust extraction and hashing mechanism.
 * The initialization is triggered lazily, often by a Mixin when a native library
 * is first accessed.
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
            throw new RuntimeException("Failed to initialize Velthoric natives", e);
        }
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