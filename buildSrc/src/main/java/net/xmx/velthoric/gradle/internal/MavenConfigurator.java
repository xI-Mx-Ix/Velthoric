/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.gradle.internal;

import net.xmx.velthoric.gradle.VelthoricExtension;
import org.gradle.api.Project;
import org.gradle.api.publish.PublishingExtension;

import java.net.URI;

/**
 * Handles the configuration of standard Maven repositories.
 * Only activates if the 'maven-publish' plugin is detected AND enabled in extension.
 *
 * @author xI-Mx-Ix
 */
public final class MavenConfigurator {

    private static final String CLOUDSMITH_URL = "https://maven.cloudsmith.io/imx-dev/velthoric/";
    private static final String REPO_NAME = "Cloudsmith";

    private MavenConfigurator() {}

    /**
     * Configures the Cloudsmith repository if enabled.
     *
     * @param project   The project to configure.
     * @param extension The Velthoric configuration.
     */
    public static void configure(Project project, VelthoricExtension extension) {
        if (!extension.getEnableCloudsmith().get()) {
            return;
        }

        project.getPluginManager().withPlugin("maven-publish", appliedPlugin -> {
            PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);

            publishing.getRepositories().maven(repo -> {
                repo.setName(REPO_NAME);
                repo.setUrl(URI.create(CLOUDSMITH_URL));

                repo.credentials(creds -> {
                    creds.setUsername(CredentialService.get(project.getRootProject(), CredentialService.KEY_CLOUDSMITH_USER));
                    creds.setPassword(CredentialService.get(project.getRootProject(), CredentialService.KEY_CLOUDSMITH_KEY));
                });
            });
        });
    }
}