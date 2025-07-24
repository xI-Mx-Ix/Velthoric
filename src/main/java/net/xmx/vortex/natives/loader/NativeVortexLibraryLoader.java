package net.xmx.vortex.natives.loader;

import electrostatic4j.snaploader.LibraryInfo;
import electrostatic4j.snaploader.LoadingCriterion;
import electrostatic4j.snaploader.NativeBinaryLoader;
import electrostatic4j.snaploader.filesystem.DirectoryPath;
import electrostatic4j.snaploader.platform.NativeDynamicLibrary;
import electrostatic4j.snaploader.platform.util.PlatformPredicate;
import net.minecraftforge.fml.loading.FMLPaths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

public final class NativeVortexLibraryLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(NativeVortexLibraryLoader.class);
    private static volatile boolean loaded = false;
    private static final Object lock = new Object();

    private NativeVortexLibraryLoader() {}

    public static void load() {
        synchronized (lock) {
            if (loaded) {
                return;
            }

            try {
                LOGGER.debug("Initializing Snaploader for automatic extraction of vortex natives...");

                Path extractionPath = FMLPaths.GAMEDIR.get()
                        .resolve("vortex")
                        .resolve("natives");
                Files.createDirectories(extractionPath);
                DirectoryPath extractionDir = new DirectoryPath(extractionPath.toString());

                LOGGER.debug("Native library extraction path set to: {}", extractionPath.toAbsolutePath());

                LibraryInfo info = new LibraryInfo(
                        DirectoryPath.CLASS_PATH,
                        null,
                        "vortex",
                        extractionDir
                );

                NativeBinaryLoader loader = new NativeBinaryLoader(info);

                NativeDynamicLibrary[] libraries = {
                        new NativeDynamicLibrary("natives/windows-x86_64", PlatformPredicate.WIN_X86_64),
                        new NativeDynamicLibrary("natives/linux-x86_64", PlatformPredicate.LINUX_X86_64),
                        new NativeDynamicLibrary("natives/linux-aarch64", PlatformPredicate.LINUX_ARM_64),
                        new NativeDynamicLibrary("natives/macos-x86_64", PlatformPredicate.MACOS_X86_64),
                        new NativeDynamicLibrary("natives/macos-aarch64", PlatformPredicate.MACOS_ARM_64)
                };

                loader.registerNativeLibraries(libraries)
                        .initPlatformLibrary();

                loader.loadLibrary(LoadingCriterion.CLEAN_EXTRACTION);

                LOGGER.info("Successfully loaded native vortex library via Snaploader.");
                loaded = true;

            } catch (Exception e) {
                LOGGER.error("Snaploader failed to find or load native vortex library from classpath", e);
                throw new RuntimeException("Snaploader could not load the native Vortex library from JAR.", e);
            }
        }
    }
}
