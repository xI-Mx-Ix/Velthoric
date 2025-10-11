/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.builtin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.builtin.block.BlockRenderer;
import net.xmx.velthoric.builtin.block.BlockRigidBody;
import net.xmx.velthoric.builtin.box.BoxRenderer;
import net.xmx.velthoric.builtin.box.BoxRigidBody;
import net.xmx.velthoric.builtin.cloth.ClothRenderer;
import net.xmx.velthoric.builtin.cloth.ClothSoftBody;
import net.xmx.velthoric.builtin.drivable.car.CarImpl;
import net.xmx.velthoric.builtin.drivable.car.CarRenderer;
import net.xmx.velthoric.builtin.drivable.motorcycle.MotorcycleImpl;
import net.xmx.velthoric.builtin.drivable.motorcycle.MotorcycleRenderer;
import net.xmx.velthoric.builtin.marble.MarbleRenderer;
import net.xmx.velthoric.builtin.marble.MarbleRigidBody;
import net.xmx.velthoric.builtin.rope.RopeRenderer;
import net.xmx.velthoric.builtin.rope.RopeSoftBody;
import net.xmx.velthoric.builtin.sphere.SphereRenderer;
import net.xmx.velthoric.builtin.sphere.SphereRigidBody;
import net.xmx.velthoric.physics.object.registry.VxObjectType;
import net.xmx.velthoric.physics.object.registry.VxObjectRegistry;

/**
 * A central registry for all built-in physics object types. This class handles
 * the registration of server-side object logic and client-side rendering factories.
 *
 * @author xI-Mx-Ix
 */
@SuppressWarnings("unchecked")
public class VxRegisteredObjects {

    public static final VxObjectType<BlockRigidBody> BLOCK = VxObjectType.Builder
            .<BlockRigidBody>create(BlockRigidBody::new)
            .noSummon()
            .build(new ResourceLocation("velthoric", "block"));

    public static final VxObjectType<SphereRigidBody> SPHERE = VxObjectType.Builder
            .<SphereRigidBody>create(SphereRigidBody::new)
            .build(new ResourceLocation("velthoric", "sphere"));

    public static final VxObjectType<BoxRigidBody> BOX = VxObjectType.Builder
            .<BoxRigidBody>create(BoxRigidBody::new)
            .build(new ResourceLocation("velthoric", "box"));

    public static final VxObjectType<MarbleRigidBody> MARBLE = VxObjectType.Builder
            .<MarbleRigidBody>create(MarbleRigidBody::new)
            .build(new ResourceLocation("velthoric", "marble"));

    public static final VxObjectType<ClothSoftBody> CLOTH = VxObjectType.Builder
            .<ClothSoftBody>create(ClothSoftBody::new)
            .build(new ResourceLocation("velthoric", "cloth"));

    public static final VxObjectType<RopeSoftBody> ROPE = VxObjectType.Builder
            .<RopeSoftBody>create(RopeSoftBody::new)
            .build(new ResourceLocation("velthoric", "rope"));

    public static final VxObjectType<CarImpl> CAR = VxObjectType.Builder
            .<CarImpl>create(CarImpl::new)
            .build(new ResourceLocation("velthoric", "car"));

    public static final VxObjectType<MotorcycleImpl> MOTORCYCLE = VxObjectType.Builder
            .<MotorcycleImpl>create(MotorcycleImpl::new)
            .build(new ResourceLocation("velthoric", "motorcycle"));

    /**
     * Registers all server-side physics object types. This should be called
     * during the server initialization phase.
     */
    public static void register() {
        var registry = VxObjectRegistry.getInstance();
        registry.register(BLOCK);
        registry.register(SPHERE);
        registry.register(BOX);
        registry.register(MARBLE);
        registry.register(CLOTH);
        registry.register(ROPE);
        registry.register(CAR);
        registry.register(MOTORCYCLE);
    }

    /**
     * Registers all client-side factory methods for creating client-side
     * representations of physics objects. This should only be called on the client.
     */
    @Environment(EnvType.CLIENT)
    public static void registerClientFactories() {
        var registry = VxObjectRegistry.getInstance();
        registry.registerClientFactory(BLOCK.getTypeId(), (type, id) -> new BlockRigidBody((VxObjectType<BlockRigidBody>) type, id));
        registry.registerClientFactory(SPHERE.getTypeId(), (type, id) -> new SphereRigidBody((VxObjectType<SphereRigidBody>) type, id));
        registry.registerClientFactory(BOX.getTypeId(), (type, id) -> new BoxRigidBody((VxObjectType<BoxRigidBody>) type, id));
        registry.registerClientFactory(MARBLE.getTypeId(), (type, id) -> new MarbleRigidBody((VxObjectType<MarbleRigidBody>) type, id));
        registry.registerClientFactory(CLOTH.getTypeId(), (type, id) -> new ClothSoftBody((VxObjectType<ClothSoftBody>) type, id));
        registry.registerClientFactory(ROPE.getTypeId(), (type, id) -> new RopeSoftBody((VxObjectType<RopeSoftBody>) type, id));
        registry.registerClientFactory(CAR.getTypeId(), (type, id) -> new CarImpl((VxObjectType<CarImpl>) type, id));
        registry.registerClientFactory(MOTORCYCLE.getTypeId(), (type, id) -> new MotorcycleImpl((VxObjectType<MotorcycleImpl>) type, id));
    }

    /**
     * Registers all client-side renderers for physics objects.
     * This must be called on the client after factories are registered.
     */
    @Environment(EnvType.CLIENT)
    public static void registerClientRenderers() {
        var registry = VxObjectRegistry.getInstance();
        registry.registerClientRenderer(BLOCK.getTypeId(), new BlockRenderer());
        registry.registerClientRenderer(SPHERE.getTypeId(), new SphereRenderer());
        registry.registerClientRenderer(BOX.getTypeId(), new BoxRenderer());
        registry.registerClientRenderer(MARBLE.getTypeId(), new MarbleRenderer());
        registry.registerClientRenderer(CLOTH.getTypeId(), new ClothRenderer());
        registry.registerClientRenderer(ROPE.getTypeId(), new RopeRenderer());
        registry.registerClientRenderer(CAR.getTypeId(), new CarRenderer());
        registry.registerClientRenderer(MOTORCYCLE.getTypeId(), new MotorcycleRenderer());
    }
}