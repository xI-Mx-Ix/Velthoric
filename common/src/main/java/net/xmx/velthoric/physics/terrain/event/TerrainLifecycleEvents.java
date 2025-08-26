package net.xmx.velthoric.physics.terrain.event;

import net.minecraft.world.level.Level;
import net.xmx.velthoric.event.api.VxLevelEvent;
import net.xmx.velthoric.physics.terrain.TerrainSystem;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

public class TerrainLifecycleEvents {

    public static void registerEvents() {
        VxLevelEvent.Save.EVENT.register(TerrainLifecycleEvents::onLevelSave);
    }

    private static void onLevelSave(VxLevelEvent.Save event) {
        Level level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }

        VxPhysicsWorld world = VxPhysicsWorld.get(level.dimension());
        if (world != null) {
            TerrainSystem terrainSystem = world.getTerrainSystem();
            if (terrainSystem != null) {
                terrainSystem.saveDirtyRegions();
            }
        }
    }
}