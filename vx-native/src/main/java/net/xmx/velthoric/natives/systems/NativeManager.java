/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.natives.systems;

import net.xmx.velthoric.natives.NativeLoader;
import net.xmx.velthoric.natives.VxNativeLibrary;
import net.xmx.velthoric.natives.os.Arch;
import net.xmx.velthoric.natives.os.OS;
import net.xmx.velthoric.natives.os.UnsupportedOperatingSystemException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Central manager for loading all required native libraries for Velthoric.
 * This class ensures that all native libraries are loaded using a unified, robust
 * extraction and loading mechanism.
 *
 * @author xI-Mx-Ix
 */
public class NativeManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("Velthoric Native Manager");
    private static final List<VxNativeLibrary> LIBRARIES = new ArrayList<>();
    private static volatile boolean areNativesInitialized = false;

    /**
     * Registers a new native library to be loaded during initialization.
     * @param library The library to register.
     */
    public static void register(VxNativeLibrary library) {
        LIBRARIES.add(library);
    }

    /**
     * Initializes and loads all registered native libraries.
     * @param gameFolder The base folder of the game where natives will be extracted.
     */
    public static synchronized void initialize(Path gameFolder) {
        if (areNativesInitialized) {
            return;
        }

        LOGGER.info("Initializing Velthoric native libraries...");
        Path extractionPath = gameFolder.resolve("velthoric").resolve("natives");

        OS os = OS.detect();
        Arch arch = Arch.detect();

        if (os == null || arch == null) {
            throw new UnsupportedOperatingSystemException(
                    "Unsupported platform: " + System.getProperty("os.name") + " (" + System.getProperty("os.arch") + ")"
            );
        }

        try {
            for (VxNativeLibrary lib : LIBRARIES) {
                loadLibrary(extractionPath, lib, os, arch);
            }

            areNativesInitialized = true;
            LOGGER.info("All Velthoric native libraries initialized successfully.");
        } catch (Exception e) {
            LOGGER.error("A critical error occurred while loading native libraries.", e);
            throw new RuntimeException("Failed to initialize Velthoric natives", e);
        }
    }

    private static void loadLibrary(Path extractionPath, VxNativeLibrary lib, OS os, Arch arch) {
        String libFileName = lib.getLibraryFileName();
        String resourcePath = lib.getResourcePath(os, arch);

        LOGGER.debug("Loading native library: {} ({})", lib.getName(), libFileName);
        NativeLoader.load(extractionPath, resourcePath, libFileName);
        lib.onLoad();
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