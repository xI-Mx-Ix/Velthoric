package net.xmx.xbullet.physics.terrain.manager;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.PhysicsObjectManager;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.PhysicsObjectManagerRegistry;
import net.xmx.xbullet.physics.physicsworld.PhysicsWorld;
import net.xmx.xbullet.physics.physicsworld.PhysicsWorldRegistry;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class TerrainSystemRegistry {

    private static TerrainSystemRegistry instance;
    private final Map<ResourceKey<Level>, TerrainSystem> systems = new ConcurrentHashMap<>();
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);

    private TerrainSystemRegistry() {}

    public static TerrainSystemRegistry getInstance() {
        if (instance == null) {
            synchronized (TerrainSystemRegistry.class) {
                if (instance == null) {
                    instance = new TerrainSystemRegistry();
                }
            }
        }
        return instance;
    }

    @Nullable
    public TerrainSystem getSystemForLevel(ServerLevel level) {
        if (isShuttingDown.get()) {
            return null;
        }

        return systems.computeIfAbsent(level.dimension(), k -> {
            PhysicsWorld physicsWorld = PhysicsWorldRegistry.getInstance().getPhysicsWorld(k);
            if (physicsWorld == null || !physicsWorld.isRunning()) {
                XBullet.LOGGER.error("Cannot create TerrainSystem for {}: PhysicsWorld is not available.", k.location());
                return null;
            }

            PhysicsObjectManager manager = PhysicsObjectManagerRegistry.getInstance().getManagerForLevel(level);
            if (manager == null) {
                XBullet.LOGGER.error("Cannot create TerrainSystem for {}: PhysicsObjectManager is not available.", k.location());
                return null;
            }

            TerrainSystem system = new TerrainSystem(physicsWorld, level);
            system.initialize(manager);

            XBullet.LOGGER.debug("Created and initialized new TerrainSystem for dimension {}.", k.location());
            return system;
        });
    }

    @Nullable
    public TerrainSystem getExistingSystem(ResourceKey<Level> dimensionKey) {
        return systems.get(dimensionKey);
    }

    public void removeSystem(ResourceKey<Level> dimensionKey) {
        TerrainSystem system = systems.remove(dimensionKey);
        if (system != null) {
            system.shutdown();
            XBullet.LOGGER.debug("Removed and shut down TerrainSystem for dimension {}.", dimensionKey.location());
        }
    }

    public void shutdownAll() {
        if (!isShuttingDown.compareAndSet(false, true)) {
            return;
        }
        XBullet.LOGGER.debug("Shutting down all TerrainSystems.");
        systems.values().forEach(TerrainSystem::shutdown);
        systems.clear();
        XBullet.LOGGER.debug("All TerrainSystems have been shut down.");

        instance = null;
    }

    public Map<ResourceKey<Level>, TerrainSystem> getAllSystems() {
        return Collections.unmodifiableMap(systems);
    }
}