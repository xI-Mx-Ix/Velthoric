package net.xmx.vortex.physics.constraint.manager.event;

import net.minecraft.world.level.Level;
import net.xmx.vortex.event.api.VxChunkEvent;
import net.xmx.vortex.event.api.VxLevelEvent;
import net.xmx.vortex.physics.constraint.manager.VxConstraintManager;
import net.xmx.vortex.physics.world.VxPhysicsWorld;

import java.util.Optional;

public class ConstraintLifecycleEvents {

    public static void registerEvents() {
        VxChunkEvent.Load.EVENT.register(ConstraintLifecycleEvents::onChunkLoad);
        VxLevelEvent.Save.EVENT.register(ConstraintLifecycleEvents::onLevelSave);
    }

    private static Optional<VxConstraintManager> getConstraintManager(Level level) {
        if (level.isClientSide()) {
            return Optional.empty();
        }

        VxPhysicsWorld world = VxPhysicsWorld.get(level.dimension());
        if (world != null && world.getConstraintManager() != null) {
            return Optional.of(world.getConstraintManager());
        }
        return Optional.empty();
    }

    private static void onChunkLoad(VxChunkEvent.Load event) {
        getConstraintManager(event.getLevel()).ifPresent(manager ->
                manager.getConstraintStorage().loadConstraintsInChunk(event.getChunkPos())
        );
    }

    private static void onLevelSave(VxLevelEvent.Save event) {
        getConstraintManager(event.getLevel()).ifPresent(VxConstraintManager::saveData);
    }
}