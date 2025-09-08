package net.xmx.velthoric.physics.object.client;

import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.physics.object.VxAbstractBody;
import net.xmx.velthoric.physics.object.type.VxRigidBody;
import net.xmx.velthoric.physics.object.type.VxSoftBody;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class VxClientObjectRegistry {
    private final Map<ResourceLocation, Supplier<VxRigidBody.Renderer>> rigidRendererFactories = new ConcurrentHashMap<>();
    private final Map<ResourceLocation, Supplier<VxSoftBody.Renderer>> softRendererFactories = new ConcurrentHashMap<>();

    void registerRigidRendererFactory(ResourceLocation identifier, Supplier<VxRigidBody.Renderer> factory) {
        rigidRendererFactories.put(identifier, factory);
    }

    void registerSoftRendererFactory(ResourceLocation identifier, Supplier<VxSoftBody.Renderer> factory) {
        softRendererFactories.put(identifier, factory);
    }

    public void registerRendererFactory(ResourceLocation typeIdentifier, Supplier<VxAbstractBody.Renderer> factory) {
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

    public VxRigidBody.Renderer createRigidRenderer(ResourceLocation identifier) {
        Supplier<VxRigidBody.Renderer> factory = rigidRendererFactories.get(identifier);
        return (factory != null) ? factory.get() : null;
    }

    public VxSoftBody.Renderer createSoftRenderer(ResourceLocation identifier) {
        Supplier<VxSoftBody.Renderer> factory = softRendererFactories.get(identifier);
        return (factory != null) ? factory.get() : null;
    }
}