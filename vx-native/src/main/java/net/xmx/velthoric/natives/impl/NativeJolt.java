/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.natives.impl;

import net.xmx.velthoric.natives.VxNativeLibrary;

/**
 * Manages the lifecycle of the Jolt physics native library.
 *
 * @author xI-Mx-Ix
 */
public class NativeJolt extends VxNativeLibrary {

    @Override
    public String getName() {
        return "Jolt Physics";
    }

    @Override
    public String getLibraryId() {
        return "joltjni";
    }
}