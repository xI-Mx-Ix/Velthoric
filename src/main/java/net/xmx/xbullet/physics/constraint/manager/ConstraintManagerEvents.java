package net.xmx.xbullet.physics.constraint.manager;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.physics.physicsworld.PhysicsWorld;

@Mod.EventBusSubscriber(modid = XBullet.MODID)
public class ConstraintManagerEvents {

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {

        if (event.getLevel() instanceof ServerLevel level && !level.isClientSide()) {

            PhysicsWorld world = PhysicsWorld.get(level.dimension());

            if (world != null && world.isRunning()) {

                ConstraintManager manager = world.getConstraintManager();
                if (manager != null) {
                    manager.loadConstraintsForChunk(event.getChunk().getPos());
                }
            }
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {

        if (event.getLevel() instanceof ServerLevel level && !level.isClientSide()) {

            PhysicsWorld world = PhysicsWorld.get(level.dimension());

            if (world == null) {
                return;
            }

            ConstraintManager manager = world.getConstraintManager();
            if (manager != null) {
                manager.unloadConstraintsForChunk(event.getChunk().getPos());
            }
        }
    }
}