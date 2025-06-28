package net.xmx.xbullet.physics.object.global.physicsobject.manager;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.xmx.xbullet.init.XBullet;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PhysicsObjectManagerRegistry {

    private static PhysicsObjectManagerRegistry instance;
    private final Map<ResourceKey<Level>, PhysicsObjectManager> managers = new ConcurrentHashMap<>();

    private PhysicsObjectManagerRegistry() {
        XBullet.LOGGER.debug("Global PhysicsObjectManagerRegistry initialized.");
    }

    public static PhysicsObjectManagerRegistry getInstance() {
        if (instance == null) {
            synchronized (PhysicsObjectManagerRegistry.class) {
                if (instance == null) {
                    instance = new PhysicsObjectManagerRegistry();
                }
            }
        }
        return instance;
    }

    @Nullable
    public PhysicsObjectManager getManagerForLevel(ServerLevel level) {
        return managers.computeIfAbsent(level.dimension(), k -> {
            PhysicsObjectManager manager = new PhysicsObjectManager();
            manager.initialize(level);
            XBullet.LOGGER.debug("Created new PhysicsObjectManager for dimension {}.", k.location());
            return manager;
        });
    }

    @Nullable
    public PhysicsObjectManager getExistingManager(ResourceKey<Level> dimensionKey) {
        return managers.get(dimensionKey);
    }

    public void removeManager(ResourceKey<Level> dimensionKey) {
        PhysicsObjectManager manager = managers.remove(dimensionKey);
        if (manager != null) {
            XBullet.LOGGER.debug("Removed PhysicsObjectManager for dimension {}.", dimensionKey.location());
            manager.shutdown();
        }
    }

    public void shutdownAll() {
        XBullet.LOGGER.debug("Shutting down all PhysicsObjectManagers.");
        for (ResourceKey<Level> key : Collections.unmodifiableSet(managers.keySet())) {
            removeManager(key);
        }
        managers.clear();
        XBullet.LOGGER.debug("All PhysicsObjectManagers shut down.");
    }

    public Map<ResourceKey<Level>, PhysicsObjectManager> getAllManagers() {
        return Collections.unmodifiableMap(managers);
    }
}
