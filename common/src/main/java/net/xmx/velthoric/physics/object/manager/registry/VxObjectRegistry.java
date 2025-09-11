/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.manager.registry;

import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.object.VxObjectType;
import net.xmx.velthoric.physics.object.VxAbstractBody;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A thread-safe singleton registry for managing physics object types.
 * This class holds mappings from a unique {@link ResourceLocation} to a corresponding
 * {@link VxObjectType}, which contains the factory for creating instances of that object type.
 *
 * @author xI-Mx-Ix
 */
public class VxObjectRegistry {

    /** The singleton instance of the registry. Volatile to ensure visibility across threads. */
    private static volatile VxObjectRegistry instance;

    /** A concurrent map storing the registered object types against their unique identifiers. */
    private final Map<ResourceLocation, VxObjectType<?>> registeredTypes = new ConcurrentHashMap<>();

    /**
     * Private constructor to enforce the singleton pattern.
     */
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
     * Registers a new physics object type. If a type with the same ID is already registered,
     * it will be overwritten and a warning will be logged.
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
     * @return A new {@link VxAbstractBody} instance, or null if the type ID is not registered or creation fails.
     */
    @Nullable
    public VxAbstractBody create(ResourceLocation typeId, VxPhysicsWorld world, UUID id) {
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
     * @return A new, deserialized {@link VxAbstractBody} instance, or null if creation fails.
     */
    @Nullable
    public VxAbstractBody createAndDeserialize(ResourceLocation typeId, UUID id, VxPhysicsWorld world, VxByteBuf data) {
        VxAbstractBody obj = create(typeId, world, id);
        if (obj != null) {
            if (data != null) {
                data.resetReaderIndex();
                obj.readCreationData(data);
            }
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
     *
     * @return An immutable map of all registered types.
     */
    public Map<ResourceLocation, VxObjectType<?>> getRegisteredTypes() {
        return Map.copyOf(registeredTypes);
    }
}