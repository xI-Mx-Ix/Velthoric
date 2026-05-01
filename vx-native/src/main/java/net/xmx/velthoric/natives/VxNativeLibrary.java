/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.natives;

import net.xmx.velthoric.natives.os.Arch;
import net.xmx.velthoric.natives.os.OS;

/**
 * Represents a native library that can be loaded by the Velthoric native system.
 * Subclasses define the specific metadata for each library.
 *
 * @author xI-Mx-Ix
 */
public abstract class VxNativeLibrary {

    /**
     * @return The human-readable name of the library (e.g., "Jolt Physics").
     */
    public abstract String getName();

    /**
     * @return The simple library name used for mapping to filenames (e.g., "joltjni").
     */
    public abstract String getLibraryId();

    /**
     * Resolves the resource path within the JAR for the given platform.
     * Defaults to the standard Velthoric structure: /natives/{os}/{arch}/{mappedName}
     */
    public String getResourcePath(OS os, Arch arch) {
        String libFileName = System.mapLibraryName(getLibraryId());
        return String.format("/natives/%s/%s/%s", os.folder, arch.folder, libFileName);
    }

    /**
     * @return The final filename of the library on disk (e.g., "joltjni.dll").
     */
    public String getLibraryFileName() {
        return System.mapLibraryName(getLibraryId());
    }

    /**
     * Called immediately after the library is successfully loaded into the JVM.
     * Use this for side effects like notifying other libraries that loading is complete.
     */
    public void onLoad() {}
}