/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.builtin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.builtin.block.BlockClientRigidBody;
import net.xmx.velthoric.builtin.block.BlockRenderer;
import net.xmx.velthoric.builtin.block.BlockRigidBody;
import net.xmx.velthoric.builtin.box.BoxClientRigidBody;
import net.xmx.velthoric.builtin.box.BoxRenderer;
import net.xmx.velthoric.builtin.box.BoxRigidBody;
import net.xmx.velthoric.builtin.cloth.ClothClientSoftBody;
import net.xmx.velthoric.builtin.cloth.ClothRenderer;
import net.xmx.velthoric.builtin.cloth.ClothSoftBody;
import net.xmx.velthoric.builtin.drivable.car.CarImpl;
import net.xmx.velthoric.builtin.drivable.car.CarRenderer;
import net.xmx.velthoric.builtin.drivable.motorcycle.MotorcycleImpl;
import net.xmx.velthoric.builtin.drivable.motorcycle.MotorcycleRenderer;
import net.xmx.velthoric.builtin.marble.MarbleClientRigidBody;
import net.xmx.velthoric.builtin.marble.MarbleRenderer;
import net.xmx.velthoric.builtin.marble.MarbleRigidBody;
import net.xmx.velthoric.builtin.rope.RopeClientSoftBody;
import net.xmx.velthoric.builtin.rope.RopeRenderer;
import net.xmx.velthoric.builtin.rope.RopeSoftBody;
import net.xmx.velthoric.builtin.sphere.SphereClientRigidBody;
import net.xmx.velthoric.builtin.sphere.SphereRenderer;
import net.xmx.velthoric.builtin.sphere.SphereRigidBody;
import net.xmx.velthoric.physics.object.VxObjectType;
import net.xmx.velthoric.physics.object.registry.VxObjectRegistry;
import net.xmx.velthoric.physics.vehicle.type.car.VxClientCar;
import net.xmx.velthoric.physics.vehicle.type.motorcycle.VxClientMotorcycle;

/**
 * A central registry for all built-in physics object types. This class handles
 * the registration of server-side object logic and client-side rendering factories.
 *
 * @author xI-Mx-Ix
 */
public class VxRegisteredObjects {

    public static final VxObjectType<BlockRigidBody> BLOCK = VxObjectType.Builder
            .create(BlockRigidBody::new)
            .noSummon()
            .build(new ResourceLocation("velthoric", "block"));

    public static final VxObjectType<SphereRigidBody> SPHERE = VxObjectType.Builder
            .create(SphereRigidBody::new)
            .build(new ResourceLocation("velthoric", "sphere"));

    public static final VxObjectType<BoxRigidBody> BOX = VxObjectType.Builder
            .create(BoxRigidBody::new)
            .build(new ResourceLocation("velthoric", "box"));

    public static final VxObjectType<MarbleRigidBody> MARBLE = VxObjectType.Builder
            .create(MarbleRigidBody::new)
            .build(new ResourceLocation("velthoric", "marble"));

    public static final VxObjectType<ClothSoftBody> CLOTH = VxObjectType.Builder
            .create(ClothSoftBody::new)
            .build(new ResourceLocation("velthoric", "cloth"));

    public static final VxObjectType<RopeSoftBody> ROPE = VxObjectType.Builder
            .create(RopeSoftBody::new)
            .build(new ResourceLocation("velthoric", "rope"));

    public static final VxObjectType<CarImpl> CAR = VxObjectType.Builder
            .create(CarImpl::new)
            .build(new ResourceLocation("velthoric", "car"));

    public static final VxObjectType<MotorcycleImpl> MOTORCYCLE = VxObjectType.Builder
            .create(MotorcycleImpl::new)
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
        registry.registerClientFactory(BLOCK.getTypeId(), BlockClientRigidBody::new);
        registry.registerClientFactory(SPHERE.getTypeId(), SphereClientRigidBody::new);
        registry.registerClientFactory(BOX.getTypeId(), BoxClientRigidBody::new);
        registry.registerClientFactory(MARBLE.getTypeId(), MarbleClientRigidBody::new);
        registry.registerClientFactory(CLOTH.getTypeId(), ClothClientSoftBody::new);
        registry.registerClientFactory(ROPE.getTypeId(), RopeClientSoftBody::new);
        registry.registerClientFactory(CAR.getTypeId(), VxClientCar::new);
        registry.registerClientFactory(MOTORCYCLE.getTypeId(), VxClientMotorcycle::new);
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