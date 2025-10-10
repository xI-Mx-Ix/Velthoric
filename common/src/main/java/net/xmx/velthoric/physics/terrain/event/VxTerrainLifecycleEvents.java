/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain.event;

import net.minecraft.world.level.Level;
import net.xmx.velthoric.event.api.VxLevelEvent;
import net.xmx.velthoric.physics.terrain.VxTerrainSystem;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

/**
 * Handles server-level events related to the terrain system, such as saving.
 *
 * @author xI-Mx-Ix
 */
public final class VxTerrainLifecycleEvents {

    public static void registerEvents() {
        VxLevelEvent.Save.EVENT.register(VxTerrainLifecycleEvents::onLevelSave);
    }

    private static void onLevelSave(VxLevelEvent.Save event) {
        Level level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }

        VxPhysicsWorld world = VxPhysicsWorld.get(level.dimension());
        if (world != null) {
            VxTerrainSystem terrainSystem = world.getTerrainSystem();
            if (terrainSystem != null) {
                terrainSystem.saveDirtyRegions();
            }
        }
    }
}