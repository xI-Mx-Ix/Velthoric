/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.config;

import dev.architectury.platform.Platform;
import net.xmx.velthoric.config.subsystems.VxNetworkConfig;
import net.xmx.velthoric.config.subsystems.VxPhysicsConfig;
import net.xmx.velthoric.config.subsystems.VxTerrainConfig;
import net.xmx.velthoric.init.VxMainClass;

import java.nio.file.Path;

/**
 * Central configuration handler.
 * Holds references to the specific subsystem configurations.
 * <p>
 * This class is initialized via {@link #init()} during the mod construction phase,
 * and loaded via {@link #load()} during the setup phase.
 *
 * @author xI-Mx-Ix
 */
public class VxModConfig {

    private static VxConfigSpec SPEC;

    public static VxPhysicsConfig PHYSICS;
    public static VxTerrainConfig TERRAIN;
    public static VxNetworkConfig NETWORK;

    /**
     * Initializes the configuration structure.
     * Must be called during the mod's constructor or early initialization.
     */
    public static void init() {
        VxConfigSpec.Builder builder = new VxConfigSpec.Builder();
        
        String modVersion = Platform.getMod(VxMainClass.MODID).getVersion();
        builder.setVersion(modVersion);

        builder.push("physics_core");
        PHYSICS = new VxPhysicsConfig(builder);
        builder.pop();

        builder.push("terrain_system");
        TERRAIN = new VxTerrainConfig(builder);
        builder.pop();

        builder.push("networking");
        NETWORK = new VxNetworkConfig(builder);
        builder.pop();

        SPEC = builder.build();
    }

    /**
     * Loads the configuration file from disk.
     * Must be called after {@link #init()}.
     */
    public static void load() {
        if (SPEC == null) {
            throw new IllegalStateException("VxModConfig.init() must be called before load()!");
        }
        Path configPath = Platform.getConfigFolder().resolve(VxMainClass.MODID).resolve(VxMainClass.MODID + ".json");
        SPEC.load(configPath);
    }
}