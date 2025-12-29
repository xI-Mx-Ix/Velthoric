/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.vxnative;

import java.util.Locale;

/**
 * Enumerates the supported operating systems and their corresponding directory names.
 *
 * @author xI-Mx-Ix
 */
public enum OS {
    WINDOWS("win"),
    OSX("osx"),
    LINUX("linux");

    public final String folder;

    OS(String folder) { this.folder = folder; }

    /**
     * Detects the current operating system.
     *
     * @return the corresponding {@link OS} enum, or {@code null} if the OS is not supported.
     */
    public static OS detect() {
        String osName = System.getProperty("os.name", "generic").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) return WINDOWS;
        if (osName.contains("mac")) return OSX;
        if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) return LINUX;
        return null;
    }
}