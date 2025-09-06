package net.xmx.velthoric.natives;

import java.util.Locale;

enum Arch {
        X86_64("x86-64"),
        AARCH64("aarch64");

        final String folder;

        Arch(String folder) { this.folder = folder; }

        static Arch detect() {
            String archName = System.getProperty("os.arch", "generic").toLowerCase(Locale.ROOT);
            if (archName.equals("x86_64") || archName.equals("amd64")) return X86_64;
            if (archName.equals("aarch64") || archName.equals("arm64")) return AARCH64;
            return null;
        }
    }