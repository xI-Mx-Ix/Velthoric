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
import net.xmx.velthoric.physics.body.registry.VxBodyType;
import net.xmx.velthoric.physics.body.registry.VxBodyRegistry;

/**
 * A central registry for all built-in physics body types. This class handles
 * the registration of server-side body logic and client-side rendering factories.
 *
 * @author xI-Mx-Ix
 */
@SuppressWarnings("unchecked")
public class VxRegisteredBodies {

    public static final VxBodyType<BlockRigidBody> BLOCK = VxBodyType.Builder
            .<BlockRigidBody>create(BlockRigidBody::new)
            .noSummon()
            .build(new ResourceLocation("velthoric", "block"));

    public static final VxBodyType<SphereRigidBody> SPHERE = VxBodyType.Builder
            .<SphereRigidBody>create(SphereRigidBody::new)
            .build(new ResourceLocation("velthoric", "sphere"));

    public static final VxBodyType<BoxRigidBody> BOX = VxBodyType.Builder
            .<BoxRigidBody>create(BoxRigidBody::new)
            .build(new ResourceLocation("velthoric", "box"));

    public static final VxBodyType<MarbleRigidBody> MARBLE = VxBodyType.Builder
            .<MarbleRigidBody>create(MarbleRigidBody::new)
            .build(new ResourceLocation("velthoric", "marble"));

    public static final VxBodyType<ClothSoftBody> CLOTH = VxBodyType.Builder
            .<ClothSoftBody>create(ClothSoftBody::new)
            .build(new ResourceLocation("velthoric", "cloth"));

    public static final VxBodyType<RopeSoftBody> ROPE = VxBodyType.Builder
            .<RopeSoftBody>create(RopeSoftBody::new)
            .build(new ResourceLocation("velthoric", "rope"));

    public static final VxBodyType<CarImpl> CAR = VxBodyType.Builder
            .<CarImpl>create(CarImpl::new)
            .build(new ResourceLocation("velthoric", "car"));

    public static final VxBodyType<MotorcycleImpl> MOTORCYCLE = VxBodyType.Builder
            .<MotorcycleImpl>create(MotorcycleImpl::new)
            .build(new ResourceLocation("velthoric", "motorcycle"));

    /**
     * Registers all server-side physics body types. This should be called
     * during the server initialization phase.
     */
    public static void register() {
        var registry = VxBodyRegistry.getInstance();
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
     * Registers factories for client-side instantiation of physics bodies.
     * This should only be called on the client.
     */
    @Environment(EnvType.CLIENT)
    public static void registerClientFactories() {
        var registry = VxBodyRegistry.getInstance();
        registry.registerClientFactory(BLOCK.getTypeId(), (type, id) -> new BlockRigidBody((VxBodyType<BlockRigidBody>) type, id));
        registry.registerClientFactory(SPHERE.getTypeId(), (type, id) -> new SphereRigidBody((VxBodyType<SphereRigidBody>) type, id));
        registry.registerClientFactory(BOX.getTypeId(), (type, id) -> new BoxRigidBody((VxBodyType<BoxRigidBody>) type, id));
        registry.registerClientFactory(MARBLE.getTypeId(), (type, id) -> new MarbleRigidBody((VxBodyType<MarbleRigidBody>) type, id));
        registry.registerClientFactory(CLOTH.getTypeId(), (type, id) -> new ClothSoftBody((VxBodyType<ClothSoftBody>) type, id));
        registry.registerClientFactory(ROPE.getTypeId(), (type, id) -> new RopeSoftBody((VxBodyType<RopeSoftBody>) type, id));
        registry.registerClientFactory(CAR.getTypeId(), (type, id) -> new CarImpl((VxBodyType<CarImpl>) type, id));
        registry.registerClientFactory(MOTORCYCLE.getTypeId(), (type, id) -> new MotorcycleImpl((VxBodyType<MotorcycleImpl>) type, id));
    }

    /**
     * Registers all client-side renderers for physics bodies.
     * This must be called on the client after factories are registered.
     */
    @Environment(EnvType.CLIENT)
    public static void registerClientRenderers() {
        var registry = VxBodyRegistry.getInstance();
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