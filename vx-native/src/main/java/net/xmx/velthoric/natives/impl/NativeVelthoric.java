/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.natives.impl;

import net.xmx.velthoric.natives.VxNativeLibrary;

/**
 * Manages the lifecycle of the custom Velthoric native library.
 *
 * @author xI-Mx-Ix
 */
public class NativeVelthoric extends VxNativeLibrary {

    @Override
    public String getName() {
        return "Core";
    }

    @Override
    public String getLibraryId() {
        return "vxnative";
    }
}