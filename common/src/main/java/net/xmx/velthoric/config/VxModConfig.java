/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.config;

import dev.architectury.platform.Platform;
import net.xmx.velthoric.config.subsystems.VxClientConfig;
import net.xmx.velthoric.config.subsystems.VxNetworkConfig;
import net.xmx.velthoric.config.subsystems.VxPhysicsConfig;
import net.xmx.velthoric.config.subsystems.VxTerrainConfig;
import net.xmx.velthoric.init.VxMainClass;

import java.nio.file.Path;

/**
 * Central configuration handler.
 * Holds references to the specific subsystem configurations and manages separate
 * specifications for Common (Server/Synced) and Client-only settings.
 * <p>
 * This class is initialized via {@link #init()} during the mod construction phase.
 * Loading is split into {@link #loadCommon()} (during common init) and
 * {@link #loadClient()} (during client init).
 *
 * @author xI-Mx-Ix
 */
public class VxModConfig {

    private static VxConfigSpec COMMON_SPEC;
    private static VxConfigSpec CLIENT_SPEC;

    public static VxPhysicsConfig PHYSICS;
    public static VxTerrainConfig TERRAIN;
    public static VxNetworkConfig NETWORK;
    public static VxClientConfig CLIENT;

    /**
     * Initializes the configuration structures.
     * Must be called during the mod's constructor or early initialization.
     */
    public static void init() {
        String modVersion = Platform.getMod(VxMainClass.MODID).getVersion();

        // --- Build Common Specification (Physics, Terrain, Network) ---
        VxConfigSpec.Builder commonBuilder = new VxConfigSpec.Builder();
        commonBuilder.setVersion(modVersion);

        commonBuilder.push("physics_core");
        PHYSICS = new VxPhysicsConfig(commonBuilder);
        commonBuilder.pop();

        commonBuilder.push("terrain_system");
        TERRAIN = new VxTerrainConfig(commonBuilder);
        commonBuilder.pop();

        commonBuilder.push("networking");
        NETWORK = new VxNetworkConfig(commonBuilder);
        commonBuilder.pop();

        COMMON_SPEC = commonBuilder.build();

        // --- Build Client Specification (Rendering, Input) ---
        VxConfigSpec.Builder clientBuilder = new VxConfigSpec.Builder();
        clientBuilder.setVersion(modVersion);

        clientBuilder.push("client_settings");
        CLIENT = new VxClientConfig(clientBuilder);
        clientBuilder.pop();

        CLIENT_SPEC = clientBuilder.build();
    }

    /**
     * Loads the common configuration file (velthoric-common.json) from disk.
     * Must be called after {@link #init()} during the common initialization phase.
     */
    public static void loadCommon() {
        if (COMMON_SPEC == null) {
            throw new IllegalStateException("VxModConfig.init() must be called before loadCommon()!");
        }
        Path configFolder = Platform.getConfigFolder().resolve(VxMainClass.MODID);
        COMMON_SPEC.load(configFolder.resolve("velthoric-common.json"));
    }

    /**
     * Loads the client configuration file (velthoric-client.json) from disk.
     * Must be called after {@link #init()} during the client initialization phase.
     * Should only be called on the physical client.
     */
    public static void loadClient() {
        if (CLIENT_SPEC == null) {
            throw new IllegalStateException("VxModConfig.init() must be called before loadClient()!");
        }
        Path configFolder = Platform.getConfigFolder().resolve(VxMainClass.MODID);
        CLIENT_SPEC.load(configFolder.resolve("velthoric-client.json"));
    }
}