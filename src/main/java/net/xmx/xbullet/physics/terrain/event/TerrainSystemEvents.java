package net.xmx.xbullet.physics.terrain.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.xmx.xbullet.physics.physicsworld.PhysicsWorld;
import net.xmx.xbullet.physics.terrain.manager.TerrainSystem;

public class TerrainSystemEvents {

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide()) {
            return;
        }

        Level level = event.level;
        TerrainSystem terrainSystem = PhysicsWorld.getTerrainSystem(level.dimension());
        if (terrainSystem != null) {

            terrainSystem.tick();
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && !level.isClientSide()) {
            TerrainSystem terrainSystem = PhysicsWorld.getTerrainSystem(level.dimension());
            if (terrainSystem != null) {
                terrainSystem.onChunkLoad(event.getChunk());
            }
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && !level.isClientSide()) {
            TerrainSystem terrainSystem = PhysicsWorld.getTerrainSystem(level.dimension());
            if (terrainSystem != null) {
                terrainSystem.onChunkUnload(event.getChunk());
            }
        }
    }
}