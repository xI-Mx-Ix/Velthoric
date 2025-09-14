/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.client;

import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.physics.object.VxAbstractBody;
import net.xmx.velthoric.physics.object.type.VxRigidBody;
import net.xmx.velthoric.physics.object.type.VxSoftBody;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Manages the client-side registration of renderer factories for different physics object types.
 * This allows for associating a specific {@link ResourceLocation} with a supplier that can create
 * a renderer instance for that object type.
 *
 * @author xI-Mx-Ix
 */
public class VxClientObjectRegistry {
    // A map of rigid body type identifiers to their corresponding renderer factories.
    private final Map<ResourceLocation, Supplier<VxRigidBody.Renderer>> rigidRendererFactories = new ConcurrentHashMap<>();
    // A map of soft body type identifiers to their corresponding renderer factories.
    private final Map<ResourceLocation, Supplier<VxSoftBody.Renderer>> softRendererFactories = new ConcurrentHashMap<>();

    /**
     * Registers a factory for creating a rigid body renderer.
     *
     * @param identifier The unique ID of the rigid body type.
     * @param factory    A supplier that creates a new renderer instance.
     */
    void registerRigidRendererFactory(ResourceLocation identifier, Supplier<VxRigidBody.Renderer> factory) {
        rigidRendererFactories.put(identifier, factory);
    }

    /**
     * Registers a factory for creating a soft body renderer.
     *
     * @param identifier The unique ID of the soft body type.
     * @param factory    A supplier that creates a new renderer instance.
     */
    void registerSoftRendererFactory(ResourceLocation identifier, Supplier<VxSoftBody.Renderer> factory) {
        softRendererFactories.put(identifier, factory);
    }

    /**
     * A generic method to register a renderer factory. It automatically determines whether
     * the renderer is for a rigid or soft body and registers it accordingly.
     *
     * @param typeIdentifier The unique ID of the physics object type.
     * @param factory        A supplier that creates a new renderer instance.
     * @throws IllegalArgumentException if the factory does not produce a valid renderer type.
     */
    public void registerRendererFactory(ResourceLocation typeIdentifier, Supplier<VxAbstractBody.Renderer> factory) {
        // We create a temporary instance to check its type.
        if (factory.get() instanceof VxRigidBody.Renderer) {
            this.registerRigidRendererFactory(typeIdentifier,
                    () -> (VxRigidBody.Renderer) factory.get());
        } else if (factory.get() instanceof VxSoftBody.Renderer) {
            this.registerSoftRendererFactory(typeIdentifier,
                    () -> (VxSoftBody.Renderer) factory.get());
        } else {
            throw new IllegalArgumentException("Renderer factory must be an instance of VxRigidBody.Renderer or VxSoftBody.Renderer.");
        }
    }

    /**
     * Creates a new rigid body renderer instance for the given type identifier.
     *
     * @param identifier The unique ID of the rigid body type.
     * @return A new {@link VxRigidBody.Renderer} instance, or null if no factory is registered.
     */
    public VxRigidBody.Renderer createRigidRenderer(ResourceLocation identifier) {
        Supplier<VxRigidBody.Renderer> factory = rigidRendererFactories.get(identifier);
        return (factory != null) ? factory.get() : null;
    }

    /**
     * Creates a new soft body renderer instance for the given type identifier.
     *
     * @param identifier The unique ID of the soft body type.
     * @return A new {@link VxSoftBody.Renderer} instance, or null if no factory is registered.
     */
    public VxSoftBody.Renderer createSoftRenderer(ResourceLocation identifier) {
        Supplier<VxSoftBody.Renderer> factory = softRendererFactories.get(identifier);
        return (factory != null) ? factory.get() : null;
    }
}