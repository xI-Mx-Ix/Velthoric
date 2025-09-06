package net.xmx.velthoric.physics.object.client;

import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.physics.object.type.VxRigidBody;
import net.xmx.velthoric.physics.object.type.VxSoftBody;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class VxClientObjectRegistry {
    private final Map<ResourceLocation, Supplier<VxRigidBody.Renderer>> rigidRendererFactories = new ConcurrentHashMap<>();
    private final Map<ResourceLocation, Supplier<VxSoftBody.Renderer>> softRendererFactories = new ConcurrentHashMap<>();

    public void registerRigidRendererFactory(ResourceLocation identifier, Supplier<VxRigidBody.Renderer> factory) {
        rigidRendererFactories.put(identifier, factory);
    }

    public void registerSoftRendererFactory(ResourceLocation identifier, Supplier<VxSoftBody.Renderer> factory) {
        softRendererFactories.put(identifier, factory);
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