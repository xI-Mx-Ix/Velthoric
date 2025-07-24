package net.xmx.vortex.physics.constraint.manager.events;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.xmx.vortex.physics.constraint.manager.ConstraintLifecycleManager;
import net.xmx.vortex.physics.constraint.manager.VxConstraintManager;
import net.xmx.vortex.physics.world.VxPhysicsWorld;
import java.util.Optional;

public class ConstraintLifecycleEvents {

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && !level.isClientSide()) {
            getLifecycleManager(level).ifPresent(manager -> manager.handleChunkUnload(event.getChunk().getPos()));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLevelSave(LevelEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel level) {
            getLifecycleManager(level).ifPresent(ConstraintLifecycleManager::handleLevelSave);
        }
    }

    private static Optional<ConstraintLifecycleManager> getLifecycleManager(Level level) {
        VxPhysicsWorld world = VxPhysicsWorld.get(level.dimension());
        if (world != null && world.isRunning()) {
            VxConstraintManager constraintManager = world.getConstraintManager();
            if (constraintManager != null && constraintManager.isInitialized()) {
                return Optional.ofNullable(constraintManager.getLifecycleManager());
            }
        }
        return Optional.empty();
    }
}