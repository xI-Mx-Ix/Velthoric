/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.registry;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.body.client.body.renderer.VxBodyRenderer;
import net.xmx.velthoric.physics.body.type.VxBody;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A thread-safe, common singleton registry for managing physics body types and their client-side counterparts.
 * <p>
 * This class serves as the central hub for defining and creating physics bodies.
 * On the server, it maps a {@link ResourceLocation} to a {@link VxBodyType} for creating {@link VxBody} instances.
 * <p>
 * On the client, it maps the same {@link ResourceLocation} to both a {@link ClientFactory} for instantiating
 * a client-side {@link VxBody} and a {@link VxBodyRenderer} for handling its visual representation.
 *
 * @author xI-Mx-Ix
 */
public class VxBodyRegistry {

    /** The singleton instance. */
    private static volatile VxBodyRegistry instance;

    /** Stores the core definition of each body type, used on both server and client. */
    private final Map<ResourceLocation, VxBodyType<?>> registeredTypes = new ConcurrentHashMap<>();

    /** Private constructor to enforce the singleton pattern. */
    private VxBodyRegistry() {}

    /**
     * Gets the singleton instance of the registry.
     *
     * @return The singleton {@link VxBodyRegistry} instance.
     */
    public static VxBodyRegistry getInstance() {
        if (instance == null) {
            synchronized (VxBodyRegistry.class) {
                if (instance == null) {
                    instance = new VxBodyRegistry();
                }
            }
        }
        return instance;
    }

    // --- COMMON / SERVER-SIDE METHODS ---

    /**
     * Registers a new physics body type definition.
     *
     * @param type The {@link VxBodyType} to register.
     */
    public void register(VxBodyType<?> type) {
        if (registeredTypes.containsKey(type.getTypeId())) {
            VxMainClass.LOGGER.warn("VxBodyType '{}' is already registered. Overwriting.", type.getTypeId());
        }
        registeredTypes.put(type.getTypeId(), type);
    }

    /**
     * Creates a new server-side instance of a physics body.
     *
     * @param typeId The unique identifier of the body type to create.
     * @param world  The physics world the body will belong to.
     * @param id     The unique UUID for the new body instance.
     * @return A new {@link VxBody} instance, or {@code null} on failure.
     */
    @Nullable
    public VxBody create(ResourceLocation typeId, VxPhysicsWorld world, UUID id) {
        VxBodyType<?> type = registeredTypes.get(typeId);
        if (type == null) {
            VxMainClass.LOGGER.error("No VxBodyType registered for ID: {}", typeId);
            return null;
        }
        try {
            return type.create(world, id);
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Failed to create physics body of type {}", typeId, e);
            return null;
        }
    }

    /**
     * Retrieves the registration data for a given type ID.
     *
     * @param typeId The identifier of the type.
     * @return The {@link VxBodyType}, or {@code null} if not found.
     */
    @Nullable
    public VxBodyType<?> getRegistrationData(ResourceLocation typeId) {
        return registeredTypes.get(typeId);
    }

    /**
     * Gets an immutable copy of all registered body types.
     *
     * @return An immutable map of all registered types.
     */
    public Map<ResourceLocation, VxBodyType<?>> getRegisteredTypes() {
        return Map.copyOf(registeredTypes);
    }

    // --- CLIENT-SIDE METHODS ---

    /**
     * A functional interface for a factory that creates client-side physics bodies.
     */
    @FunctionalInterface
    @Environment(EnvType.CLIENT)
    public interface ClientFactory {
        /**
         * Creates a new client-side body instance.
         *
         * @param type The type definition of the body.
         * @param id   The unique ID of the body instance.
         * @return A new instance of a {@link VxBody} subclass, configured for the client.
         */
        VxBody create(VxBodyType<?> type, UUID id);
    }

    /**
     * This inner class holds all client-only fields and logic.
     * It will not be loaded by the server, preventing the NoSuchFieldError.
     */
    @Environment(EnvType.CLIENT)
    private static class Client {
        /** Stores factories for creating client-side body instances. */
        private static final Map<ResourceLocation, ClientFactory> clientFactories = new ConcurrentHashMap<>();

        /** Stores renderers for client-side bodies. */
        private static final Map<ResourceLocation, VxBodyRenderer<?>> clientRenderers = new ConcurrentHashMap<>();
    }

    /**
     * Registers a factory for creating a client-side body instance.
     *
     * @param typeId  The unique ID of the physics body type.
     * @param factory A factory that creates a new client body instance.
     */
    @Environment(EnvType.CLIENT)
    public void registerClientFactory(ResourceLocation typeId, ClientFactory factory) {
        Client.clientFactories.put(typeId, factory);
    }

    /**
     * Registers a renderer for a specific client-side body type.
     *
     * @param typeId   The unique ID of the physics body type.
     * @param renderer An instance of the renderer for this body type.
     */
    @Environment(EnvType.CLIENT)
    public void registerClientRenderer(ResourceLocation typeId, VxBodyRenderer<?> renderer) {
        Client.clientRenderers.put(typeId, renderer);
    }

    /**
     * Creates a new client-side body instance for the given type.
     *
     * @param type The body type definition.
     * @param id   The unique identifier for the new instance.
     * @return A new {@link VxBody} instance, or {@code null} on failure.
     */
    @Nullable
    @Environment(EnvType.CLIENT)
    public VxBody createClientBody(VxBodyType<?> type, UUID id) {
        if (type == null) {
            VxMainClass.LOGGER.error("Attempted to create client body with null type for ID: {}", id);
            return null;
        }
        ResourceLocation typeId = type.getTypeId();
        ClientFactory factory = Client.clientFactories.get(typeId);
        if (factory != null) {
            try {
                return factory.create(type, id);
            } catch (Exception e) {
                VxMainClass.LOGGER.error("Failed to create client body of type {}", typeId, e);
            }
        } else {
            VxMainClass.LOGGER.error("No client factory registered for VxBodyType ID: {}", typeId);
        }
        return null;
    }

    /**
     * Retrieves the renderer for a given client-side body type.
     *
     * @param typeId The unique ID of the body type.
     * @return The registered {@link VxBodyRenderer}, or {@code null} if not found.
     */
    @Nullable
    @Environment(EnvType.CLIENT)
    public VxBodyRenderer<?> getClientRenderer(ResourceLocation typeId) {
        return Client.clientRenderers.get(typeId);
    }
}