package net.xmx.xbullet.natives;

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

public class NativeLibraryLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(NativeLibraryLoader.class);
    private static volatile boolean loaded = false;
    private static final Object lock = new Object();

    public static void load() {
        synchronized (lock) {
            if (loaded) {
                return;
            }

            try {
                LOGGER.debug("Initializing Snaploader for automatic extraction from classpath...");

                Path extractionPath = FMLPaths.GAMEDIR.get().resolve("xbullet").resolve("natives");
                Files.createDirectories(extractionPath);
                DirectoryPath extractionDir = new DirectoryPath(extractionPath.toString());
                LOGGER.debug("Native library extraction path set to: {}", extractionPath.toAbsolutePath());

                LibraryInfo info = new LibraryInfo(
                        DirectoryPath.CLASS_PATH,
                        null,
                        "joltjni",
                        extractionDir
                );

                NativeBinaryLoader loader = new NativeBinaryLoader(info);


                NativeDynamicLibrary[] libraries = {
                        new NativeDynamicLibrary("linux/aarch64/com/github/stephengold", PlatformPredicate.LINUX_ARM_64),
                        new NativeDynamicLibrary("linux/x86-64/com/github/stephengold", PlatformPredicate.LINUX_X86_64),
                        new NativeDynamicLibrary("osx/aarch64/com/github/stephengold", PlatformPredicate.MACOS_ARM_64),
                        new NativeDynamicLibrary("osx/x86-64/com/github/stephengold", PlatformPredicate.MACOS_X86_64),
                        new NativeDynamicLibrary("windows/x86-64/com/github/stephengold", PlatformPredicate.WIN_X86_64)
                };

                loader.registerNativeLibraries(libraries)
                        .initPlatformLibrary();

                loader.loadLibrary(LoadingCriterion.CLEAN_EXTRACTION);

                loaded = true;

            } catch (Exception e) {
                LOGGER.error("Snaploader failed to find or load native library from classpath", e);
                throw new RuntimeException("Snaploader could not load the native Jolt library from JAR.", e);
            }
        }
    }
}