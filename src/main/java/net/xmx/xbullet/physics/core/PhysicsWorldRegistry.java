package net.xmx.xbullet.physics.core;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.xmx.xbullet.init.XBullet;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PhysicsWorldRegistry {

    private static PhysicsWorldRegistry instance;
    private final Map<ResourceKey<Level>, PhysicsWorld> physicsWorlds = new ConcurrentHashMap<>();

    private PhysicsWorldRegistry() {
        XBullet.LOGGER.debug("PhysicsWorldManager initialized.");
    }

    public static PhysicsWorldRegistry getInstance() {
        if (instance == null) {
            synchronized (PhysicsWorldRegistry.class) {
                if (instance == null) {
                    instance = new PhysicsWorldRegistry();
                }
            }
        }
        return instance;
    }

    public void initializeForDimension(ServerLevel level) {
        if (level == null) {
            XBullet.LOGGER.error("Attempted to initialize PhysicsWorld for null level.");
            return;
        }
        ResourceKey<Level> dimensionKey = level.dimension();
        if (physicsWorlds.containsKey(dimensionKey)) {
            XBullet.LOGGER.warn("PhysicsWorld for dimension {} is already initialized.", dimensionKey.location());
            return;
        }

        XBullet.LOGGER.debug("Initializing PhysicsWorld for dimension {}.", dimensionKey.location());
        PhysicsWorld world = new PhysicsWorld(dimensionKey);
        physicsWorlds.put(dimensionKey, world);
        world.initialize();
    }

    public void shutdownForDimension(ResourceKey<Level> dimensionKey) {
        PhysicsWorld world = physicsWorlds.remove(dimensionKey);
        if (world != null) {
            XBullet.LOGGER.debug("Shutting down PhysicsWorld for dimension {}.", dimensionKey.location());
            world.stop();
        }
    }

    public void shutdownAll() {
        XBullet.LOGGER.debug("Shutting down all PhysicsWorlds.");

        for (ResourceKey<Level> dimensionKey : Collections.unmodifiableSet(physicsWorlds.keySet())) {
            shutdownForDimension(dimensionKey);
        }
        physicsWorlds.clear();
        XBullet.LOGGER.debug("All PhysicsWorlds shut down.");
    }

    @Nullable
    public PhysicsWorld getPhysicsWorld(ResourceKey<Level> dimensionKey) {
        return physicsWorlds.get(dimensionKey);
    }

    public Map<ResourceKey<Level>, PhysicsWorld> getAllPhysicsWorlds() {
        return Collections.unmodifiableMap(physicsWorlds);
    }

    public boolean isDimensionPhysicsRunning(ResourceKey<Level> dimensionKey) {
        PhysicsWorld world = physicsWorlds.get(dimensionKey);
        return world != null && world.isRunning();
    }
}