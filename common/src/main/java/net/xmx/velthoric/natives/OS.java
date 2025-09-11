/*
This file is part of Velthoric.
Licensed under LGPL 3.0.
*/
package net.xmx.velthoric.natives;

import java.util.Locale;

enum OS {
        WINDOWS("windows"),
        OSX("osx"),
        LINUX("linux");

        final String folder;

        OS(String folder) { this.folder = folder; }

        static OS detect() {
            String osName = System.getProperty("os.name", "generic").toLowerCase(Locale.ROOT);
            if (osName.contains("win")) return WINDOWS;
            if (osName.contains("mac")) return OSX;
            if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) return LINUX;
            return null;
        }
    }