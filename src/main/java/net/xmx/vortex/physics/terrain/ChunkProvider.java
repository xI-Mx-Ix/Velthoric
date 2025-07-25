package net.xmx.vortex.physics.terrain;

import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkProvider {

    private static final Map<ServerLevel, TerrainSystem> registeredTerrainSystems = new ConcurrentHashMap<>();

    public static void registerTerrainSystem(ServerLevel level, TerrainSystem terrainSystem) {
        registeredTerrainSystems.put(level, terrainSystem);
    }

    public static void unregisterTerrainSystem(ServerLevel level) {
        registeredTerrainSystems.remove(level);
    }

    public static void processSnapshotsForLevel(ServerLevel level) {
        TerrainSystem system = registeredTerrainSystems.get(level);
        if (system != null) {
            system.processPendingSnapshotsOnMainThread();
        }
    }
}