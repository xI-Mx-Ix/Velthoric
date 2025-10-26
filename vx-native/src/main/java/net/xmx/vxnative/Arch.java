/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.vxnative;

import java.util.Locale;

/**
 * @author xI-Mx-Ix
 */
public enum Arch {
    X86_64("x86-64"),
    AARCH64("aarch64");

    public final String folder;

    Arch(String folder) {
        this.folder = folder;
    }

    public static Arch detect() {
        String archName = System.getProperty("os.arch", "generic").toLowerCase(Locale.ROOT);
        if (archName.equals("x86_64") || archName.equals("amd64")) return X86_64;
        if (archName.equals("aarch64") || archName.equals("arm64")) return AARCH64;
        return null;
    }
}