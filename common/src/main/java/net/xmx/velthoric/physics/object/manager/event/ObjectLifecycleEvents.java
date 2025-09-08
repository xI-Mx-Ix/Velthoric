package net.xmx.velthoric.physics.object.manager.event;

import net.minecraft.world.level.Level;
import net.xmx.velthoric.event.api.VxChunkEvent;
import net.xmx.velthoric.event.api.VxLevelEvent;
import net.xmx.velthoric.physics.object.VxAbstractBody;
import net.xmx.velthoric.physics.object.manager.VxObjectManager;
import net.xmx.velthoric.physics.object.manager.VxRemovalReason;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.List;
import java.util.Optional;

public class ObjectLifecycleEvents {

    public static void registerEvents() {
        VxChunkEvent.Load.EVENT.register(ObjectLifecycleEvents::onChunkLoad);
        VxChunkEvent.Unload.EVENT.register(ObjectLifecycleEvents::onChunkUnload);
        VxLevelEvent.Save.EVENT.register(ObjectLifecycleEvents::onLevelSave);
    }

    private static Optional<VxObjectManager> getObjectManager(Level level) {
        if (level.isClientSide()) {
            return Optional.empty();
        }

        VxPhysicsWorld world = VxPhysicsWorld.get(level.dimension());
        if (world != null && world.getObjectManager() != null) {
            return Optional.of(world.getObjectManager());
        }
        return Optional.empty();
    }

    private static void onChunkLoad(VxChunkEvent.Load event) {
        getObjectManager(event.getLevel()).ifPresent(manager ->
                manager.getObjectStorage().loadObjectsInChunk(event.getChunkPos())
        );
    }

    private static void onChunkUnload(VxChunkEvent.Unload event) {
        getObjectManager(event.getLevel()).ifPresent(manager -> {
            List<VxAbstractBody> objectsInChunk = manager.getObjectsInChunk(event.getChunkPos());
            for (VxAbstractBody obj : objectsInChunk) {
                manager.removeObject(obj.getPhysicsId(), VxRemovalReason.SAVE);
            }
        });
    }

    private static void onLevelSave(VxLevelEvent.Save event) {
        getObjectManager(event.getLevel()).ifPresent(manager -> {
            manager.getAllObjects()
                    .forEach(manager.getObjectStorage()::storeObject);
            manager.getObjectStorage().saveDirtyRegions();
        });
    }
}