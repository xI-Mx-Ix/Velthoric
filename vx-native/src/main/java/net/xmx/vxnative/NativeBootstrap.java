/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.vxnative;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Utility class for loading native libraries.
 *
 * @author xI-Mx-Ix
 */
public class NativeBootstrap {

    private static final Logger LOGGER = LoggerFactory.getLogger("Velthoric NativeBootstrap");

    /**
     * Loads a native library directly from the filesystem using the standard system loader.
     * <p>
     * This method uses {@link System#load(String)} to load the library from an absolute path.
     * The library is loaded into the JVM process and becomes globally available.
     * <p>
     *
     * @param libFile The native library file to load.
     */
    public static void loadLibrary(File libFile) {
        String absPath = libFile.getAbsolutePath();
        LOGGER.debug("Loading native library via System.load(): {}", absPath);

        try {
            System.load(absPath);
            LOGGER.debug("Successfully loaded native library: {}", libFile.getName());
        } catch (Throwable e) {
            LOGGER.error("Failed to load native library '{}'", absPath, e);
            throw new RuntimeException("Could not load native library: " + absPath, e);
        }
    }
}