package net.xmx.velthoric.physics.object.client;

import net.xmx.velthoric.physics.object.type.VxRigidBody;
import net.xmx.velthoric.physics.object.type.VxSoftBody;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class VxClientObjectRegistry {
    private final Map<String, Supplier<VxRigidBody.Renderer>> rigidRendererFactories = new ConcurrentHashMap<>();
    private final Map<String, Supplier<VxSoftBody.Renderer>> softRendererFactories = new ConcurrentHashMap<>();

    public void registerRigidRendererFactory(String identifier, Supplier<VxRigidBody.Renderer> factory) {
        rigidRendererFactories.put(identifier, factory);
    }

    public void registerSoftRendererFactory(String identifier, Supplier<VxSoftBody.Renderer> factory) {
        softRendererFactories.put(identifier, factory);
    }

    public VxRigidBody.Renderer createRigidRenderer(String identifier) {
        Supplier<VxRigidBody.Renderer> factory = rigidRendererFactories.get(identifier);
        return (factory != null) ? factory.get() : null;
    }

    public VxSoftBody.Renderer createSoftRenderer(String identifier) {
        Supplier<VxSoftBody.Renderer> factory = softRendererFactories.get(identifier);
        return (factory != null) ? factory.get() : null;
    }
}