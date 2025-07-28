package net.xmx.vortex.api;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.xmx.vortex.physics.object.physicsobject.EObjectType;
import net.xmx.vortex.physics.object.physicsobject.IPhysicsObject;
import net.xmx.vortex.physics.object.physicsobject.client.ClientPhysicsObjectManager;
import net.xmx.vortex.physics.object.physicsobject.properties.IPhysicsObjectProperties;
import net.xmx.vortex.physics.object.physicsobject.registry.GlobalPhysicsObjectRegistry;
import net.xmx.vortex.physics.object.physicsobject.type.rigid.RigidPhysicsObject;
import net.xmx.vortex.physics.object.physicsobject.type.soft.SoftPhysicsObject;

import java.util.function.Supplier;

public class PhysicsObjectModule {

    public void registerObjectType(String typeId, EObjectType objectType, IPhysicsObjectProperties properties, Class<? extends IPhysicsObject> clazz) {
        if (typeId == null || typeId.trim().isEmpty()) {
            throw new IllegalArgumentException("PhysicsObject typeId cannot be null or empty.");
        }
        if (objectType == null) {
            throw new IllegalArgumentException("PhysicsObject objectType cannot be null for typeId: " + typeId);
        }
        if (properties == null) {
            throw new IllegalArgumentException("PhysicsObject properties cannot be null for typeId: " + typeId);
        }
        if (clazz == null) {
            throw new IllegalArgumentException("PhysicsObject class cannot be null for typeId: " + typeId);
        }

        GlobalPhysicsObjectRegistry.register(typeId, objectType, properties, clazz);
    }

    @Environment(EnvType.CLIENT)
    public void registerRigidRenderer(String typeIdentifier, Supplier<RigidPhysicsObject.Renderer> factory) {
        if (typeIdentifier == null || typeIdentifier.trim().isEmpty()) {
            throw new IllegalArgumentException("PhysicsObject typeIdentifier cannot be null or empty.");
        }
        if (factory == null) {
            throw new IllegalArgumentException("Renderer factory cannot be null for typeIdentifier: " + typeIdentifier);
        }

        ClientPhysicsObjectManager.getInstance().registerRigidRendererFactory(typeIdentifier, factory);
    }

    @Environment(EnvType.CLIENT)
    public void registerSoftRenderer(String typeIdentifier, Supplier<SoftPhysicsObject.Renderer> factory) {
        if (typeIdentifier == null || typeIdentifier.trim().isEmpty()) {
            throw new IllegalArgumentException("PhysicsObject typeIdentifier cannot be null or empty.");
        }
        if (factory == null) {
            throw new IllegalArgumentException("Renderer factory cannot be null for typeIdentifier: " + typeIdentifier);
        }

        ClientPhysicsObjectManager.getInstance().registerSoftRendererFactory(typeIdentifier, factory);
    }
}