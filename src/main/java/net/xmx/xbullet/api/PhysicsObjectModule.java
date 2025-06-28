package net.xmx.xbullet.api;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.xmx.xbullet.physics.object.global.physicsobject.EObjectType;
import net.xmx.xbullet.physics.object.global.physicsobject.IPhysicsObject;
import net.xmx.xbullet.physics.object.global.physicsobject.client.ClientPhysicsObjectManager;
import net.xmx.xbullet.physics.object.global.physicsobject.properties.IPhysicsObjectProperties;
import net.xmx.xbullet.physics.object.global.physicsobject.registry.GlobalPhysicsObjectRegistry;
import net.xmx.xbullet.physics.object.rigidphysicsobject.RigidPhysicsObject;
import net.xmx.xbullet.physics.object.rigidphysicsobject.builder.RigidPhysicsObjectBuilder;
import net.xmx.xbullet.physics.object.softphysicsobject.SoftPhysicsObject;
import net.xmx.xbullet.physics.object.softphysicsobject.builder.SoftPhysicsObjectBuilder;

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

    @OnlyIn(Dist.CLIENT)
    public void registerRigidRenderer(String typeIdentifier, Supplier<RigidPhysicsObject.Renderer> factory) {
        if (typeIdentifier == null || typeIdentifier.trim().isEmpty()) {
            throw new IllegalArgumentException("PhysicsObject typeIdentifier cannot be null or empty.");
        }
        if (factory == null) {
            throw new IllegalArgumentException("Renderer factory cannot be null for typeIdentifier: " + typeIdentifier);
        }

        ClientPhysicsObjectManager.getInstance().registerRigidRendererFactory(typeIdentifier, factory);
    }

    @OnlyIn(Dist.CLIENT)
    public void registerSoftRenderer(String typeIdentifier, Supplier<SoftPhysicsObject.Renderer> factory) {
        if (typeIdentifier == null || typeIdentifier.trim().isEmpty()) {
            throw new IllegalArgumentException("PhysicsObject typeIdentifier cannot be null or empty.");
        }
        if (factory == null) {
            throw new IllegalArgumentException("Renderer factory cannot be null for typeIdentifier: " + typeIdentifier);
        }

        ClientPhysicsObjectManager.getInstance().registerSoftRendererFactory(typeIdentifier, factory);
    }

    public RigidPhysicsObjectBuilder createRigidObjectBuilder() {
        return new RigidPhysicsObjectBuilder();
    }

    public SoftPhysicsObjectBuilder createSoftObjectBuilder() {
        return new SoftPhysicsObjectBuilder();
    }
}