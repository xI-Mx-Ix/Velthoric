/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.registry;

import com.github.stephengold.joltjni.enumerate.EBodyType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.object.VxObjectType;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.object.client.body.VxClientBody;
import net.xmx.velthoric.physics.object.client.body.renderer.VxBodyRenderer;
import net.xmx.velthoric.physics.object.type.VxBody;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A thread-safe, common singleton registry for managing physics object types and their client-side counterparts.
 * <p>
 * This class serves as the central hub for defining and creating physics objects.
 * On the server, it maps a {@link ResourceLocation} to a {@link VxObjectType} for creating {@link VxBody} instances.
 * <p>
 * On the client, it maps the same {@link ResourceLocation} to both a {@link ClientFactory} for instantiating
 * the {@link VxClientBody} and a {@link VxBodyRenderer} for handling its visual representation.
 *
 * @author xI-Mx-Ix
 */
public class VxObjectRegistry {

    /** The singleton instance. */
    private static volatile VxObjectRegistry instance;

    /** Stores the core definition of each object type, used on both server and client. */
    private final Map<ResourceLocation, VxObjectType<?>> registeredTypes = new ConcurrentHashMap<>();

    /** Stores factories for creating client-side body instances. This map is only populated and used on the client. */
    @Environment(EnvType.CLIENT)
    private final Map<ResourceLocation, ClientFactory> clientFactories = new ConcurrentHashMap<>();

    /** Stores renderers for client-side bodies. This map is only populated and used on the client. */
    @Environment(EnvType.CLIENT)
    private final Map<ResourceLocation, VxBodyRenderer<?>> clientRenderers = new ConcurrentHashMap<>();

    /** Private constructor to enforce the singleton pattern. */
    private VxObjectRegistry() {}

    /**
     * Gets the singleton instance of the registry.
     *
     * @return The singleton {@link VxObjectRegistry} instance.
     */
    public static VxObjectRegistry getInstance() {
        if (instance == null) {
            synchronized (VxObjectRegistry.class) {
                if (instance == null) {
                    instance = new VxObjectRegistry();
                }
            }
        }
        return instance;
    }

    // --- COMMON / SERVER-SIDE METHODS ---

    /**
     * Registers a new physics object type definition.
     *
     * @param type The {@link VxObjectType} to register.
     */
    public void register(VxObjectType<?> type) {
        if (registeredTypes.containsKey(type.getTypeId())) {
            VxMainClass.LOGGER.warn("VxObjectType '{}' is already registered. Overwriting.", type.getTypeId());
        }
        registeredTypes.put(type.getTypeId(), type);
    }

    /**
     * Creates a new server-side instance of a physics object.
     *
     * @param typeId The unique identifier of the object type to create.
     * @param world  The physics world the object will belong to.
     * @param id     The unique UUID for the new object instance.
     * @return A new {@link VxBody} instance, or {@code null} on failure.
     */
    @Nullable
    public VxBody create(ResourceLocation typeId, VxPhysicsWorld world, UUID id) {
        VxObjectType<?> type = registeredTypes.get(typeId);
        if (type == null) {
            VxMainClass.LOGGER.error("No VxObjectType registered for ID: {}", typeId);
            return null;
        }
        try {
            return type.create(world, id);
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Failed to create physics object of type {}", typeId, e);
            return null;
        }
    }

    /**
     * Retrieves the registration data for a given type ID.
     *
     * @param typeId The identifier of the type.
     * @return The {@link VxObjectType}, or {@code null} if not found.
     */
    @Nullable
    public VxObjectType<?> getRegistrationData(ResourceLocation typeId) {
        return registeredTypes.get(typeId);
    }

    /**
     * Gets an immutable copy of all registered object types.
     *
     * @return An immutable map of all registered types.
     */
    public Map<ResourceLocation, VxObjectType<?>> getRegisteredTypes() {
        return Map.copyOf(registeredTypes);
    }

    // --- CLIENT-SIDE METHODS ---

    /**
     * A functional interface for a factory that creates client-side physics objects.
     */
    @FunctionalInterface
    @Environment(EnvType.CLIENT)
    public interface ClientFactory {
        /**
         * Creates a new client-side body instance.
         *
         * @param id             The unique ID of the object.
         * @param typeId         The unique identifier of the object's type.
         * @param manager        The client object manager instance.
         * @param dataStoreIndex The index assigned to this object in the client data store.
         * @param objectType     The type of the body (e.g., RigidBody, SoftBody).
         * @return A new instance of a {@link VxClientBody} subclass.
         */
        VxClientBody create(UUID id, ResourceLocation typeId, VxClientObjectManager manager, int dataStoreIndex, EBodyType objectType);
    }

    /**
     * Registers a factory for creating a client-side body instance.
     *
     * @param typeId  The unique ID of the physics object type.
     * @param factory A factory that creates a new client body instance.
     */
    @Environment(EnvType.CLIENT)
    public void registerClientFactory(ResourceLocation typeId, ClientFactory factory) {
        clientFactories.put(typeId, factory);
    }

    /**
     * Registers a renderer for a specific client-side body type.
     *
     * @param typeId   The unique ID of the physics object type.
     * @param renderer An instance of the renderer for this object type.
     */
    @Environment(EnvType.CLIENT)
    public void registerClientRenderer(ResourceLocation typeId, VxBodyRenderer<?> renderer) {
        clientRenderers.put(typeId, renderer);
    }

    /**
     * Creates a new client-side body instance for the given type identifier.
     *
     * @return A new {@link VxClientBody} instance, or {@code null} on failure.
     */
    @Nullable
    @Environment(EnvType.CLIENT)
    public VxClientBody createClientBody(ResourceLocation typeId, UUID id, VxClientObjectManager manager, int dataStoreIndex, EBodyType objectType) {
        ClientFactory factory = clientFactories.get(typeId);
        if (factory != null) {
            try {
                return factory.create(id, typeId, manager, dataStoreIndex, objectType);
            } catch (Exception e) {
                VxMainClass.LOGGER.error("Failed to create client body of type {}", typeId, e);
            }
        } else {
            VxMainClass.LOGGER.error("No client factory registered for VxObjectType ID: {}", typeId);
        }
        return null;
    }

    /**
     * Retrieves the renderer for a given client-side body type.
     *
     * @param typeId The unique ID of the physics object type.
     * @return The registered {@link VxBodyRenderer}, or {@code null} if not found.
     */
    @Nullable
    @Environment(EnvType.CLIENT)
    public VxBodyRenderer<?> getClientRenderer(ResourceLocation typeId) {
        return clientRenderers.get(typeId);
    }
}