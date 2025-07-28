package net.xmx.vortex.physics.constraint.manager.events;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.xmx.vortex.event.api.VxChunkEvent;
import net.xmx.vortex.event.api.VxLevelEvent;
import net.xmx.vortex.physics.constraint.manager.ConstraintLifecycleManager;
import net.xmx.vortex.physics.constraint.manager.VxConstraintManager;
import net.xmx.vortex.physics.world.VxPhysicsWorld;

import java.util.Optional;

public class ConstraintLifecycleEvents {

    public static void registerEvents() {
        VxChunkEvent.Unload.EVENT.register(ConstraintLifecycleEvents::onChunkUnload);
        VxLevelEvent.Save.EVENT.register(ConstraintLifecycleEvents::onLevelSave);
    }

    private static void onChunkUnload(VxChunkEvent.Unload event) {
        ServerLevel level = event.getLevel();
        if (!level.isClientSide()) {
            getLifecycleManager(level).ifPresent(manager -> manager.handleChunkUnload(event.getChunkPos()));
        }
    }

    private static void onLevelSave(VxLevelEvent.Save event) {
        ServerLevel level = event.getLevel();
        getLifecycleManager(level).ifPresent(ConstraintLifecycleManager::handleLevelSave);
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
