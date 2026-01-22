/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.natives;

import net.xmx.vxnative.NativeBootstrap;
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

/**
 * A generic loader for native libraries from the classpath.
 * It extracts a native library to a specified path, performs a hash check
 * to avoid unnecessary re-extraction, and then loads it into the JVM
 * using the NativeBootstrap mechanism.
 *
 * @author xI-Mx-Ix
 */
public class NativeLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger("Velthoric Native Loader");

    /**
     * Loads a native library, extracting it if necessary.
     * <p>
     * Once the file is verified or extracted, this method delegates the actual
     * loading to {@link NativeBootstrap#loadLibrary(java.io.File)} to ensure
     * correct ClassLoader handling.
     *
     * @param extractionPath The directory to extract the library into.
     * @param resourcePath   The full path to the library within the classpath (e.g., "/natives/windows/x86-64/mylib.dll").
     * @param libFileName    The desired file name for the extracted library (e.g., "mylib.dll").
     */
    public static void load(Path extractionPath, String resourcePath, String libFileName) {
        try {
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

                // ClassLoader expects path without leading slash
                String pathForClassLoader = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
                try (InputStream source = Thread.currentThread().getContextClassLoader().getResourceAsStream(pathForClassLoader)) {
                    if (source == null) {
                        throw new IOException("Native library '" + resourcePath + "' not found in classpath.");
                    }
                    Files.copy(source, targetFile, StandardCopyOption.REPLACE_EXISTING);
                }
            }

            // Delegate loading to the NativeBootstrap to handle ClassLoader registration
            NativeBootstrap.loadLibrary(targetFile.toFile());
            LOGGER.debug("Successfully loaded native library: {}", libFileName);

        } catch (Exception e) {
            LOGGER.error("Failed to load the native library '{}' from resource '{}'", libFileName, resourcePath, e);
            throw new RuntimeException("Could not load the native library: " + libFileName, e);
        }
    }

    private static String calculateHash(InputStream in) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        try (DigestInputStream dis = new DigestInputStream(in, md)) {
            // Read the stream to EOF to process all bytes
            while (dis.read(buffer) != -1) ;
        }
        return String.format("%064x", new BigInteger(1, md.digest()));
    }

    private static String calculateFileHash(Path path) throws IOException, NoSuchAlgorithmException {
        try (InputStream in = Files.newInputStream(path)) {
            return calculateHash(in);
        }
    }

    private static String calculateResourceHash(String resourcePath) throws IOException, NoSuchAlgorithmException {
        String pathForClassLoader = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(pathForClassLoader)) {
            if (in == null) {
                throw new IOException("Resource '" + resourcePath + "' not found in classpath.");
            }
            return calculateHash(in);
        }
    }
}