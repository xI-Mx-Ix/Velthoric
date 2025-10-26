/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.vxnative;

import java.util.Locale;

/**
 * @author xI-Mx-Ix
 */
public enum OS {
    WINDOWS("windows"),
    OSX("osx"),
    LINUX("linux");

    public final String folder;

    OS(String folder) { this.folder = folder; }

    public static OS detect() {
        String osName = System.getProperty("os.name", "generic").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) return WINDOWS;
        if (osName.contains("mac")) return OSX;
        if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) return LINUX;
        return null;
    }
}