package net.xmx.xbullet.natives.loader;

import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class NativeXBulletLibraryLoader {

    private static final Logger LOGGER = LogManager.getLogger("XBulletNativeLoader");
    private static boolean loaded = false;

    private NativeXBulletLibraryLoader() {}

    public static void load() {
        if (loaded) {
            return; 
        }

        try {

            String platformIdentifier = getPlatformIdentifier();
            String libraryFileName = getLibraryFileName();
            LOGGER.info("Detected Platform: {}, Required Library: {}", platformIdentifier, libraryFileName);

            String jarResourcePath = "/natives/" + platformIdentifier + "/" + libraryFileName;
            Path extractionTargetDirectory = FMLPaths.GAMEDIR.get().resolve("xbullet").resolve("natives");
            Path extractionTargetPath = extractionTargetDirectory.resolve(libraryFileName);

            extractLibrary(jarResourcePath, extractionTargetPath);

            System.load(extractionTargetPath.toAbsolutePath().toString());
            LOGGER.info("Successfully loaded native library: {}", extractionTargetPath);

            loaded = true;

        } catch (Exception e) {
            LOGGER.fatal("FATAL: Could not load native xbullet library!", e);

            throw new RuntimeException("Failed to load XBullet natives", e);
        }
    }

    private static void extractLibrary(String jarResourcePath, Path extractionTargetPath) throws IOException {
        if (Files.exists(extractionTargetPath)) {
            LOGGER.info("Native library already exists, skipping extraction: {}", extractionTargetPath);
            return;
        }

        LOGGER.info("Native library not found. Extracting to: {}", extractionTargetPath);

        Files.createDirectories(extractionTargetPath.getParent());

        try (InputStream inputStream = NativeXBulletLibraryLoader.class.getResourceAsStream(jarResourcePath)) {
            if (inputStream == null) {
                throw new IOException("Cannot find resource in JAR: " + jarResourcePath);
            }

            Files.copy(inputStream, extractionTargetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String getPlatformIdentifier() {
        String osName = System.getProperty("os.name").toLowerCase();
        String osArch = System.getProperty("os.arch").toLowerCase();

        if (osName.contains("win")) {
            if (osArch.contains("64")) return "windows-x86_64";
        } else if (osName.contains("nix") || osName.contains("nux")) {
            if (osArch.contains("aarch64")) return "linux-aarch64";
            if (osArch.contains("64")) return "linux-x86_64";
        } else if (osName.contains("mac")) {
            if (osArch.contains("aarch64")) return "macos-aarch64";
            if (osArch.contains("64")) return "macos-x86_64";
        }

        throw new UnsupportedOperationException("Unsupported OS/Architecture combination: " + osName + "/" + osArch);
    }

    private static String getLibraryFileName() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) return "xbullet.dll";
        if (osName.contains("mac")) return "libxbullet.dylib";

        return "libxbullet.so";
    }
}