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
import net.xmx.velthoric.physics.object.type.VxBody;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A thread-safe, common singleton registry for managing physics object types.
 * <p>
 * This class serves as the central hub for defining and creating physics objects.
 * On the server, it maps a {@link ResourceLocation} to a {@link VxObjectType}, which contains
 * the factory for creating server-side {@link VxBody} instances.
 * <p>
 * On the client, it additionally maps the same {@link ResourceLocation} to a {@link ClientFactory}.
 * This factory is responsible for instantiating the corresponding {@link VxClientBody} subclass,
 * which includes all client-side logic such as rendering. This dual-registry approach ensures a
 * clean separation between server-side game logic and client-side presentation.
 *
 * @author xI-Mx-Ix
 */
public class VxObjectRegistry {

    /** The singleton instance. Volatile ensures that changes are visible across all threads. */
    private static volatile VxObjectRegistry instance;

    /**
     * Stores the core definition of each object type. This map is used on both the server and client.
     * On the server, it's primarily used to create new {@link VxBody} instances.
     */
    private final Map<ResourceLocation, VxObjectType<?>> registeredTypes = new ConcurrentHashMap<>();

    /**
     * Stores factories for creating client-side body instances. This map is only populated and used on the client.
     */
    @Environment(EnvType.CLIENT)
    private final Map<ResourceLocation, ClientFactory> clientFactories = new ConcurrentHashMap<>();

    /**
     * Private constructor to enforce the singleton pattern.
     */
    private VxObjectRegistry() {}

    /**
     * Gets the singleton instance of the registry.
     * Uses double-checked locking for thread-safe lazy initialization.
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
     * Registers a new physics object type definition. This method should be called on both the server
     * and client during mod initialization to ensure type information is available on both sides.
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
     * Creates a new server-side instance of a physics object using its registered type ID.
     * This is typically called by the {@code VxObjectManager} when creating new objects in the world.
     *
     * @param typeId The unique identifier of the object type to create.
     * @param world  The physics world the object will belong to.
     * @param id     The unique UUID for the new object instance.
     * @return A new {@link VxBody} instance, or {@code null} if the type ID is not registered or creation fails.
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
     * @return The {@link VxObjectType} associated with the ID, or {@code null} if not found.
     */
    @Nullable
    public VxObjectType<?> getRegistrationData(ResourceLocation typeId) {
        return registeredTypes.get(typeId);
    }

    /**
     * Gets an immutable copy of all registered object types.
     * This is safe to use for command suggestions or iterating over all known types.
     *
     * @return An immutable map of all registered types.
     */
    public Map<ResourceLocation, VxObjectType<?>> getRegisteredTypes() {
        return Map.copyOf(registeredTypes);
    }

    // --- CLIENT-SIDE METHODS ---

    /**
     * A functional interface for a factory that creates client-side physics objects.
     * This is typically implemented with a constructor reference (e.g., {@code MyClientCarBody::new}).
     */
    @FunctionalInterface
    @Environment(EnvType.CLIENT)
    public interface ClientFactory {
        /**
         * Creates a new client-side body instance.
         *
         * @param id             The unique ID of the object.
         * @param manager        The client object manager instance.
         * @param dataStoreIndex The index assigned to this object in the client data store.
         * @param objectType     The type of the body (e.g., RigidBody, SoftBody).
         * @return A new instance of a {@link VxClientBody} subclass.
         */
        VxClientBody create(UUID id, VxClientObjectManager manager, int dataStoreIndex, EBodyType objectType);
    }

    /**
     * Registers a factory for creating a client-side body instance.
     * This must be called in the client-side mod initializer for each physics object that needs to be rendered.
     *
     * @param typeId  The unique ID of the physics object type. This must match the server-side registration.
     * @param factory A factory that creates a new client body instance.
     */
    @Environment(EnvType.CLIENT)
    public void registerClientFactory(ResourceLocation typeId, ClientFactory factory) {
        clientFactories.put(typeId, factory);
    }

    /**
     * Creates a new client-side body instance for the given type identifier.
     * This is called by the {@code VxClientObjectManager} when it receives a spawn packet from the server.
     *
     * @param typeId         The unique ID of the object type.
     * @param id             The UUID of the specific object instance.
     * @param manager        The client object manager.
     * @param dataStoreIndex The object's index in the data store.
     * @param objectType     The Jolt body type.
     * @return A new {@link VxClientBody} instance, or {@code null} if no matching factory is registered or creation fails.
     */
    @Nullable
    @Environment(EnvType.CLIENT)
    public VxClientBody createClientBody(ResourceLocation typeId, UUID id, VxClientObjectManager manager, int dataStoreIndex, EBodyType objectType) {
        ClientFactory factory = clientFactories.get(typeId);
        if (factory != null) {
            try {
                // The factory is called, which in turn calls the constructor of the specific client body class.
                return factory.create(id, manager, dataStoreIndex, objectType);
            } catch (Exception e) {
                VxMainClass.LOGGER.error("Failed to create client body of type {}", typeId, e);
            }
        } else {
            VxMainClass.LOGGER.error("No client factory registered for VxObjectType ID: {}", typeId);
        }
        return null;
    }
}