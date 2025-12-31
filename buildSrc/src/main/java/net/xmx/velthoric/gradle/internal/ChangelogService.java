/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.gradle.internal;

import org.gradle.api.Project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utility for resolving changelog content from the filesystem.
 *
 * @author xI-Mx-Ix
 */
public final class ChangelogService {

    private ChangelogService() {}

    /**
     * Reads the changelog markdown file for the specific version.
     *
     * @param project The root project containing the changelog directory.
     * @param version The version string to look up.
     * @return The content of the file, or a placeholder if IO failed.
     */
    public static String resolveChangelog(Project project, String version) {
        if (version == null || version.isEmpty()) {
            return "No version defined for changelog lookup.";
        }

        Path changelogPath = project.getRootDir().toPath()
                .resolve("changelog")
                .resolve(version + ".md");

        if (!Files.exists(changelogPath)) {
            project.getLogger().debug("VelthoricPublishing: No changelog found at {}", changelogPath);
            return "No changelog provided for version " + version;
        }

        try {
            return Files.readString(changelogPath);
        } catch (IOException e) {
            project.getLogger().error("VelthoricPublishing: Failed to read changelog.", e);
            return "Error reading changelog for version " + version;
        }
    }
}