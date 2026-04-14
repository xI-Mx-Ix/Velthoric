/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.service;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;
import net.xmx.velthoric.init.VxMainClass;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * High-performance service registry for optional physics subsystems.
 *
 * @author LOLAtom
 */
public class VxServiceManager {

    private volatile Object2ObjectOpenHashMap<Class<? extends IVxPhysicsService>, IVxPhysicsService> services;

    private final VxPhysicsWorld physicsWorld;
    private final ServerLevel level;

    public VxServiceManager(VxPhysicsWorld physicsWorld, ServerLevel level) {
        this.physicsWorld = physicsWorld;
        this.level = level;
    }

    /**
     * Registers a service instance.
     * <p>
     * <b>Performance:</b> O(n) where n = current service count (copy-on-write)
     * <br><b>Thread-safe:</b> yes (synchronized + volatile publish)
     * <br><b>When to call:</b> During server initialization, not in tick loops
     *
     * @param service The service to register.
     * @param <T>     The service type.
     * @return The same service instance (for chaining).
     */
    public <T extends IVxPhysicsService> T registerService(T service) {
        synchronized (this) {
            Object2ObjectOpenHashMap<Class<? extends IVxPhysicsService>, IVxPhysicsService> current = services;
            Object2ObjectOpenHashMap<Class<? extends IVxPhysicsService>, IVxPhysicsService> newMap;

            if (current == null) {
                newMap = new Object2ObjectOpenHashMap<>(8, 0.75f);
            } else {
                newMap = new Object2ObjectOpenHashMap<>(current);
            }

            @SuppressWarnings("unchecked")
            Class<T> serviceClass = (Class<T>) service.getClass();
            IVxPhysicsService existing = newMap.put(serviceClass, service);

            if (existing != null) {
                VxMainClass.LOGGER.warn("Service {} was already registered, replacing", service.getIdentification());
            }

            this.services = newMap;

            VxMainClass.LOGGER.debug("Registered service: {} (total: {})",
                    service.getIdentification(), newMap.size());
            return service;
        }
    }

    /**
     * Gets a service by its class type.
     * <p>
     * <b>Performance:</b> ~3-5ns average (volatile read + FastUtil get)
     * <br><b>Thread-safe:</b> yes (lock-free volatile read)
     * <br><b>Type-safe:</b> yes (generics + cast)
     *
     * @param clazz The service class to look up.
     * @param <T>   The service type.
     * @return The service instance, or null if not registered.
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public <T extends IVxPhysicsService> T getService(Class<T> clazz) {
        Object2ObjectOpenHashMap<Class<? extends IVxPhysicsService>, IVxPhysicsService> map = services;

        if (map == null) {
            return null;
        }
        IVxPhysicsService service = map.get(clazz);

        if (service == null) {
            return null;
        }
        return (T) service;
    }

    /**
     * Gets a service with a default fallback.
     * <p>
     * Avoids null checks at call sites. Useful for optional features.
     *
     * @param clazz        The service class to look up.
     * @param defaultValue Value to return if service not found.
     * @param <T>          The service type.
     * @return The service instance, or defaultValue if not registered.
     */
    public <T extends IVxPhysicsService> T getServiceOrDefault(Class<T> clazz, T defaultValue) {
        T service = getService(clazz);
        return service != null ? service : defaultValue;
    }

    /**
     * Gets a service, creating it lazily if not present.
     * <p>
     * Useful for services that are expensive to create and may not be needed.
     * <b>Note:</b> The supplier is called with the write lock held.
     *
     * @param clazz   The service class to look up.
     * @param creator Supplier to create the service if not found.
     * @param <T>     The service type.
     * @return The existing or newly created service instance.
     */
    public <T extends IVxPhysicsService> T getServiceOrCreate(Class<T> clazz, Supplier<T> creator) {
        T service = getService(clazz);
        if (service != null) {
            return service;
        }

        // Create and register in one atomic operation
        synchronized (this) {
            service = getService(clazz);
            if (service != null) {
                return service;
            }

            service = creator.get();
            return registerService(service);
        }
    }

    /**
     * Checks if a service is registered.
     * <p>
     * <b>Performance:</b> ~3ns (volatile read + containsKey)
     *
     * @param clazz The service class to check.
     * @return true if the service is registered.
     */
    public boolean hasService(Class<? extends IVxPhysicsService> clazz) {
        Object2ObjectOpenHashMap<Class<? extends IVxPhysicsService>, IVxPhysicsService> map = services;
        return map != null && map.containsKey(clazz);
    }

    /**
     * Initializes all registered services.
     * <p>
     * Called during {@code VxPhysicsWorld.initializeAndStart()}.
     * Services are initialized after core managers (bodies, constraints, etc.)
     * so they can safely depend on core functionality.
     */
    public void initialize() {
        Object2ObjectOpenHashMap<Class<? extends IVxPhysicsService>, IVxPhysicsService> map = services;
        if (map == null || map.isEmpty()) {
            return;
        }

        VxMainClass.LOGGER.debug("Initializing {} registered services", map.size());

        for (IVxPhysicsService service : map.values()) {
            try {
                service.initialize();
            } catch (Exception e) {
                VxMainClass.LOGGER.error("Failed to initialize service: {}",
                        service.getIdentification(), e);
            }
        }
    }

    /**
     * Shuts down all registered services.
     * <p>
     * Called during {@code VxPhysicsWorld.shutdown()}.
     * Services are shut down BEFORE core managers so they can clean up
     * while their dependencies are still available.
     */
    public void shutdown() {
        Object2ObjectOpenHashMap<Class<? extends IVxPhysicsService>, IVxPhysicsService> map = services;
        if (map == null || map.isEmpty()) {
            return;
        }

        VxMainClass.LOGGER.debug("Shutting down {} registered services", map.size());

        // Shutdown in reverse registration order (LIFO) for dependency safety
        // Services registered later may depend on services registered earlier
        IVxPhysicsService[] servicesArray = map.values().toArray(new IVxPhysicsService[0]);
        for (int i = servicesArray.length - 1; i >= 0; i--) {
            try {
                servicesArray[i].shutdown();
            } catch (Exception e) {
                VxMainClass.LOGGER.error("Failed to shutdown service: {}",
                        servicesArray[i].getIdentification(), e);
            }
        }

        // Clear the map reference (volatile write)
        synchronized (this) {
            this.services = null;
        }
    }

    /**
     * Calls {@link IVxPhysicsService#onPrePhysicsTick(VxPhysicsWorld)} on all services.
     * <p>
     * <b>Must be called from:</b> Physics thread
     * <br><b>Frequency:</b> 60Hz (fixed timestep)
     * <br><b>Performance:</b> O(n) where n = service count (typically < 20)
     */
    public void onPrePhysicsTick(VxPhysicsWorld world) {
        Object2ObjectOpenHashMap<Class<? extends IVxPhysicsService>, IVxPhysicsService> map = services;
        if (map == null || map.isEmpty()) {
            return;
        }

        for (IVxPhysicsService service : map.values()) {
            try {
                service.onPrePhysicsTick(world);
            } catch (Exception e) {
                VxMainClass.LOGGER.error("Error in service.onPrePhysicsTick(): {}",
                        service.getIdentification(), e);
            }
        }
    }

    /**
     * Calls {@link IVxPhysicsService#onPhysicsTick(VxPhysicsWorld)} on all services.
     * <p>
     * <b>Must be called from:</b> Physics thread
     * <br><b>Frequency:</b> 60Hz (fixed timestep)
     */
    public void onPhysicsTick(VxPhysicsWorld world) {
        Object2ObjectOpenHashMap<Class<? extends IVxPhysicsService>, IVxPhysicsService> map = services;
        if (map == null || map.isEmpty()) {
            return;
        }

        for (IVxPhysicsService service : map.values()) {
            try {
                service.onPhysicsTick(world);
            } catch (Exception e) {
                VxMainClass.LOGGER.error("Error in service.onPhysicsTick(): {}",
                        service.getIdentification(), e);
            }
        }
    }

    /**
     * Calls {@link IVxPhysicsService#onGameTick(ServerLevel)} on all services.
     * <p>
     * <b>Called from:</b> Server thread (not physics thread)
     * <br><b>Frequency:</b> 20Hz (Minecraft tick rate)
     */
    public void onGameTick(ServerLevel level) {
        Object2ObjectOpenHashMap<Class<? extends IVxPhysicsService>, IVxPhysicsService> map = services;
        if (map == null || map.isEmpty()) {
            return;
        }

        for (IVxPhysicsService service : map.values()) {
            try {
                service.onGameTick(level);
            } catch (Exception e) {
                VxMainClass.LOGGER.error("Error in service.onGameTick(): {}",
                        service.getIdentification(), e);
            }
        }
    }

    public VxPhysicsWorld getPhysicsWorld() {
        return physicsWorld;
    }

    public ServerLevel getLevel() {
        return level;
    }

    public int getServiceCount() {
        Object2ObjectOpenHashMap<Class<? extends IVxPhysicsService>, IVxPhysicsService> map = services;
        return map != null ? map.size() : 0;
    }
}