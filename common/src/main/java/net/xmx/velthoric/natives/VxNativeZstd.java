/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.natives;

import com.github.luben.zstd.util.Native;
import com.github.luben.zstd.util.ZstdVersion;
import net.xmx.vxnative.Arch;
import net.xmx.vxnative.OS;
import net.xmx.vxnative.VxNativeLibraryLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Manages the lifecycle of the Zstd-JNI native library.
 * This class hijacks the default loading mechanism of zstd-jni to use
 * Velthoric's unified native loader, ensuring consistent extraction and loading.
 *
 * @author xI-Mx-Ix
 */
public class VxNativeZstd {

    private static final Logger LOGGER = LoggerFactory.getLogger("Velthoric Zstd-JNI");
    private static volatile boolean isInitialized = false;

    /**
     * Initializes the Zstd-JNI native library using the custom loader.
     * It first tells the original zstd-jni loader to stand down using `Native.assumeLoaded()`,
     * then proceeds to extract and load the library from a consistent location.
     *
     * @param extractionPath The root directory where native libraries should be extracted.
     */
    public static void initialize(Path extractionPath) {
        if (isInitialized) {
            return;
        }

        // Prevent zstd-jni from attempting its own loading mechanism.
        // This must be called before any other Zstd class is touched.
        Native.assumeLoaded();

        String resourcePath = getNativeLibraryResourcePath();
        if (resourcePath == null) {
            throw new UnsupportedOperationException("Unsupported platform for Zstd-JNI: " +
                    System.getProperty("os.name") + " (" + System.getProperty("os.arch") + ")");
        }

        // Use a clean, version-independent name for the extracted file.
        String libFileName = System.mapLibraryName("zstd-jni");

        LOGGER.debug("Attempting to load Zstd-JNI native library...");
        VxNativeLibraryLoader.load(extractionPath, resourcePath, libFileName);

        isInitialized = true;
        LOGGER.debug("Zstd-JNI native library loaded successfully via Velthoric loader.");
    }

    /**
     * Constructs the classpath resource path for the Zstd-JNI native library.
     * This mimics the path structure used by zstd-jni itself, accounting for
     * its inconsistent architecture folder names.
     *
     * @return The full resource path, or null if the platform is unsupported.
     */
    private static String getNativeLibraryResourcePath() {
        OS os = OS.detect();
        Arch arch = Arch.detect();

        if (os == null || arch == null) return null;

        String osFolder;
        switch (os) {
            case WINDOWS:
                osFolder = "win";
                break;
            case OSX:
                osFolder = "darwin";
                break;
            default:
                osFolder = os.folder; // "linux"
                break;
        }

        String archFolder;
        // Correctly handle zstd-jni's inconsistent arch folder naming.
        if (arch == Arch.X86_64) {
            if (os == OS.OSX) {
                archFolder = "x86_64"; // As seen in the JAR for darwin
            } else {
                archFolder = "amd64"; // As seen in the JAR for win/linux
            }
        } else {
            // AARCH64 maps directly to "aarch64", which is consistent in the JAR.
            archFolder = arch.folder;
        }

        String libNameInJar = "libzstd-jni-" + ZstdVersion.VERSION;
        String libExtension;
        switch (os) {
            case WINDOWS:
                libExtension = "dll";
                break;
            case OSX:
                libExtension = "dylib";
                break;
            default:
                libExtension = "so";
                break;
        }

        return String.format("/%s/%s/%s.%s", osFolder, archFolder, libNameInJar, libExtension);
    }
}