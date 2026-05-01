/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.natives.impl;

import com.github.luben.zstd.util.Native;
import net.xmx.velthoric.natives.VxNativeLibrary;

/**
 * Manages the lifecycle of the Zstd-JNI native library.
 *
 * @author xI-Mx-Ix
 */
public class NativeZstd extends VxNativeLibrary {

    @Override
    public String getName() {
        return "Zstd-JNI";
    }

    @Override
    public String getLibraryId() {
        return "zstd-jni";
    }

    @Override
    public void onLoad() {
        // Prevent zstd-jni from attempting its own loading mechanism.
        Native.assumeLoaded();
    }
}