package net.xmx.xbullet.physics.terrain.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.xmx.xbullet.physics.terrain.manager.TerrainSystem;
import net.xmx.xbullet.physics.terrain.manager.TerrainSystemRegistry;

public class TerrainBlockEvents {

    @SubscribeEvent
    public static void onBlockChange(BlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.isClientSide()) {
            return;
        }
        TerrainSystem system = TerrainSystemRegistry.getInstance().getExistingSystem(level.dimension());
        if (system != null) {
            system.onBlockChanged(event.getPos());
        }
    }
}