/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.vxnative;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class VxNativeLibraryLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger("Velthoric Native Loader");
    private static volatile boolean loaded = false;

    public static void load(Path extractionPath) {
        if (loaded) return;

        try {
            String resourcePath = getNativeLibraryResourcePath();
            if (resourcePath == null) {
                throw new UnsupportedOperationException("Unsupported platform: " +
                        System.getProperty("os.name") + " (" + System.getProperty("os.arch") + ")");
            }

            String libFileName = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
            Path targetFile = extractionPath.resolve(libFileName);
            LOGGER.debug("Native library target path: {}", targetFile.toAbsolutePath());

            boolean needsExtraction = true;
            if (Files.exists(targetFile)) {
                String existingFileHash = calculateFileHash(targetFile);
                String resourceHash = calculateResourceHash(resourcePath);
                if (existingFileHash.equals(resourceHash)) {
                    LOGGER.debug("Native library '{}' is up-to-date. Skipping extraction.", libFileName);
                    needsExtraction = false;
                } else {
                    LOGGER.debug("Hash mismatch for '{}'. Re-extracting library.", libFileName);
                }
            }

            if (needsExtraction) {
                LOGGER.debug("Extracting native library '{}' to '{}'", resourcePath, targetFile);
                Files.createDirectories(extractionPath);

                String pathForClassLoader = resourcePath.substring(1);
                try (InputStream source = Thread.currentThread().getContextClassLoader().getResourceAsStream(pathForClassLoader)) {
                    if (source == null) throw new IOException("Native library '" + resourcePath + "' not found in classpath.");
                    Files.copy(source, targetFile, StandardCopyOption.REPLACE_EXISTING);
                }
            }

            System.load(targetFile.toAbsolutePath().toString());
            loaded = true;
            LOGGER.debug("Successfully loaded native Jolt library: {}", libFileName);

        } catch (Exception e) {
            LOGGER.error("Failed to load the native Jolt library", e);
            throw new RuntimeException("Could not load the native Jolt library.", e);
        }
    }

    private static String getNativeLibraryResourcePath() {
        OS os = OS.detect();
        Arch arch = Arch.detect();

        if (os == null || arch == null) return null;

        String libName = System.mapLibraryName("joltjni");

        return String.format("/%s/%s/com/github/stephengold/%s", os.folder, arch.folder, libName);
    }

    private static String calculateHash(InputStream in) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        try (DigestInputStream dis = new DigestInputStream(in, md)) {
            while (dis.read(buffer) != -1);
        }
        return String.format("%064x", new BigInteger(1, md.digest()));
    }

    private static String calculateFileHash(Path path) throws IOException, NoSuchAlgorithmException {
        try (InputStream in = Files.newInputStream(path)) {
            return calculateHash(in);
        }
    }

    private static String calculateResourceHash(String resourcePath) throws IOException, NoSuchAlgorithmException {

        String pathForClassLoader = resourcePath.substring(1);
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(pathForClassLoader)) {
            if (in == null) throw new IOException("Resource '" + resourcePath + "' not found in classpath.");
            return calculateHash(in);
        }
    }
}