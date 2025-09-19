/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.registry;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.object.type.VxBody;
import net.xmx.velthoric.physics.object.VxObjectType;
import net.xmx.velthoric.physics.object.type.VxRigidBody;
import net.xmx.velthoric.physics.object.type.VxSoftBody;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * A thread-safe, common singleton registry for managing physics object types and their client-side renderers.
 * This class serves both the server and the client, holding mappings from a {@link ResourceLocation}
 * to a {@link VxObjectType} for object creation, and also to client-side renderer factories.
 */
public class VxObjectRegistry {

    // The singleton instance of the registry.
    private static volatile VxObjectRegistry instance;

    // Stores the object type definitions, used on both server and client.
    private final Map<ResourceLocation, VxObjectType<?>> registeredTypes = new ConcurrentHashMap<>();

    // Stores client-side renderer factories.
    private final Map<ResourceLocation, Supplier<? extends VxBody.Renderer>> rendererFactories = new ConcurrentHashMap<>();

    // Private constructor to enforce the singleton pattern.
    private VxObjectRegistry() {}

    /**
     * Gets the singleton instance of the registry, creating it if it doesn't exist.
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

    /**
     * Registers a new physics object type. This method is called on both server and client.
     * If a type with the same ID is already registered, it will be overwritten.
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
     * Creates a new instance of a physics object using its registered type ID.
     *
     * @param typeId The unique identifier of the object type to create.
     * @param world  The physics world the object will belong to.
     * @param id     The unique UUID for the new object instance.
     * @return A new {@link VxBody} instance, or null if the type ID is not registered.
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
     * Creates a new physics object and immediately deserializes its creation-specific data.
     *
     * @param typeId The unique identifier of the object type.
     * @param id     The unique UUID for the new object instance.
     * @param world  The physics world the object will belong to.
     * @param data   A buffer containing the creation data to be read by the new object.
     * @return A new, deserialized {@link VxBody} instance, or null if creation fails.
     */
    @Nullable
    public VxBody createAndDeserialize(ResourceLocation typeId, UUID id, VxPhysicsWorld world, VxByteBuf data) {
        VxBody obj = create(typeId, world, id);
        if (obj != null && data != null) {
            data.resetReaderIndex();
            obj.readCreationData(data);
        }
        return obj;
    }

    /**
     * Retrieves the registration data for a given type ID.
     *
     * @param typeId The identifier of the type.
     * @return The {@link VxObjectType} associated with the ID, or null if not found.
     */
    @Nullable
    public VxObjectType<?> getRegistrationData(ResourceLocation typeId) {
        return registeredTypes.get(typeId);
    }

    /**
     * Gets an immutable copy of all registered object types.
     * This is safe to use for command suggestions on the client.
     *
     * @return An immutable map of all registered types.
     */
    public Map<ResourceLocation, VxObjectType<?>> getRegisteredTypes() {
        return Map.copyOf(registeredTypes);
    }

    /**
     * Registers a factory for creating a client-side renderer.
     *
     * @param typeIdentifier The unique ID of the physics object type.
     * @param factory        A supplier that creates a new renderer instance.
     */
    @Environment(EnvType.CLIENT)
    public void registerRendererFactory(ResourceLocation typeIdentifier, Supplier<? extends VxBody.Renderer> factory) {
        rendererFactories.put(typeIdentifier, factory);
    }

    /**
     * Creates a new rigid body renderer instance for the given type identifier.
     *
     * @param identifier The unique ID of the rigid body type.
     * @return A new {@link VxRigidBody.Renderer} instance, or null if no matching factory is registered.
     */
    @Nullable
    @Environment(EnvType.CLIENT)
    public VxRigidBody.Renderer createRigidRenderer(ResourceLocation identifier) {
        Supplier<? extends VxBody.Renderer> factory = rendererFactories.get(identifier);
        if (factory != null) {
            VxBody.Renderer renderer = factory.get();
            if (renderer instanceof VxRigidBody.Renderer) {
                return (VxRigidBody.Renderer) renderer;
            }
        }
        return null;
    }

    /**
     * Creates a new soft body renderer instance for the given type identifier.
     *
     * @param identifier The unique ID of the soft body type.
     * @return A new {@link VxSoftBody.Renderer} instance, or null if no matching factory is registered.
     */
    @Nullable
    @Environment(EnvType.CLIENT)
    public VxSoftBody.Renderer createSoftRenderer(ResourceLocation identifier) {
        Supplier<? extends VxBody.Renderer> factory = rendererFactories.get(identifier);
        if (factory != null) {
            VxBody.Renderer renderer = factory.get();
            if (renderer instanceof VxSoftBody.Renderer) {
                return (VxSoftBody.Renderer) renderer;
            }
        }
        return null;
    }
}