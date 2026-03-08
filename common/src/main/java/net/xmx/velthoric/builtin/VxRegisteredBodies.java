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
import net.xmx.velthoric.core.body.registry.VxBodyRegistry;
import net.xmx.velthoric.core.body.registry.VxBodyType;
import net.xmx.velthoric.core.ragdoll.body.VxBodyPartRigidBody;
import net.xmx.velthoric.core.ragdoll.body.VxRagdollBodyPartRenderer;
import net.xmx.velthoric.item.chaincreator.body.VxChainPartRenderer;
import net.xmx.velthoric.item.chaincreator.body.VxChainPartRigidBody;

/**
 * A central registry for all built-in physics body types. This class handles
 * the registration of server-side body logic and client-side rendering factories.
 * <p>
 * Each body type is defined using the composition-based {@link VxBodyType.Builder},
 * with Jolt shape providers and persistence handlers registered declaratively.
 *
 * @author xI-Mx-Ix
 */
public class VxRegisteredBodies {

    // --- Rigid Bodies ---

    public static final VxBodyType BLOCK = VxBodyType.Builder
            .create(BlockRigidBody::new)
            .noSummon()
            .rigidProvider(BlockRigidBody::createJoltBody)
            .buoyant()
            .netSync()
            .customDataSync()
            .persistence(BlockRigidBody::writePersistence, BlockRigidBody::readPersistence)
            .build(ResourceLocation.tryBuild("velthoric", "block"));

    public static final VxBodyType SPHERE = VxBodyType.Builder
            .create(SphereRigidBody::new)
            .rigidProvider(SphereRigidBody::createJoltBody)
            .buoyant()
            .netSync()
            .customDataSync()
            .persistence(SphereRigidBody::writePersistence, SphereRigidBody::readPersistence)
            .build(ResourceLocation.tryBuild("velthoric", "sphere"));

    public static final VxBodyType BOX = VxBodyType.Builder
            .create(BoxRigidBody::new)
            .rigidProvider(BoxRigidBody::createJoltBody)
            .buoyant()
            .netSync()
            .customDataSync()
            .persistence(BoxRigidBody::writePersistence, BoxRigidBody::readPersistence)
            .build(ResourceLocation.tryBuild("velthoric", "box"));

    public static final VxBodyType MARBLE = VxBodyType.Builder
            .create(MarbleRigidBody::new)
            .rigidProvider(MarbleRigidBody::createJoltBody)
            .buoyant()
            .netSync()
            .customDataSync()
            .persistence(MarbleRigidBody::writePersistence, MarbleRigidBody::readPersistence)
            .build(ResourceLocation.tryBuild("velthoric", "marble"));

    // --- Soft Bodies ---

    public static final VxBodyType CLOTH = VxBodyType.Builder
            .create(ClothSoftBody::new)
            .softProvider(ClothSoftBody::createJoltBody)
            .netSync()
            .customDataSync()
            .persistence(ClothSoftBody::writePersistence, ClothSoftBody::readPersistence)
            .build(ResourceLocation.tryBuild("velthoric", "cloth"));

    public static final VxBodyType ROPE = VxBodyType.Builder
            .create(RopeSoftBody::new)
            .softProvider(RopeSoftBody::createJoltBody)
            .netSync()
            .customDataSync()
            .persistence(RopeSoftBody::writePersistence, RopeSoftBody::readPersistence)
            .build(ResourceLocation.tryBuild("velthoric", "rope"));

    // --- Vehicles ---

    public static final VxBodyType CAR = VxBodyType.Builder
            .create(CarImpl::new)
            .rigidProvider(CarImpl::createJoltBody)
            .buoyant()
            .netSync()
            .mountable()
            .customDataSync()
            .build(ResourceLocation.tryBuild("velthoric", "car"));

    public static final VxBodyType MOTORCYCLE = VxBodyType.Builder
            .create(MotorcycleImpl::new)
            .rigidProvider(MotorcycleImpl::createJoltBody)
            .buoyant()
            .netSync()
            .mountable()
            .customDataSync()
            .build(ResourceLocation.tryBuild("velthoric", "motorcycle"));

    // --- Internal Bodies ---

    public static final VxBodyType CHAIN_PART = VxBodyType.Builder
            .create(VxChainPartRigidBody::new)
            .noSummon()
            .rigidProvider(VxChainPartRigidBody::createJoltBody)
            .buoyant()
            .netSync()
            .customDataSync()
            .build(ResourceLocation.tryBuild("velthoric", "chain_part"));

    public static final VxBodyType BODY_PART = VxBodyType.Builder
            .create(VxBodyPartRigidBody::new)
            .noSummon()
            .rigidProvider(VxBodyPartRigidBody::createJoltBody)
            .buoyant()
            .netSync()
            .customDataSync()
            .build(ResourceLocation.tryBuild("velthoric", "body_part"));

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
        registry.register(CHAIN_PART);
        registry.register(BODY_PART);
    }

    /**
     * Registers factories for client-side instantiation of physics bodies.
     * This should only be called on the client.
     */
    @Environment(EnvType.CLIENT)
    public static void registerClientFactories() {
        var registry = VxBodyRegistry.getInstance();
        registry.registerClientFactory(BLOCK.getTypeId(), (type, id) -> new BlockRigidBody(type, id));
        registry.registerClientFactory(SPHERE.getTypeId(), (type, id) -> new SphereRigidBody(type, id));
        registry.registerClientFactory(BOX.getTypeId(), (type, id) -> new BoxRigidBody(type, id));
        registry.registerClientFactory(MARBLE.getTypeId(), (type, id) -> new MarbleRigidBody(type, id));
        registry.registerClientFactory(CLOTH.getTypeId(), (type, id) -> new ClothSoftBody(type, id));
        registry.registerClientFactory(ROPE.getTypeId(), (type, id) -> new RopeSoftBody(type, id));
        registry.registerClientFactory(CAR.getTypeId(), (type, id) -> new CarImpl(type, id));
        registry.registerClientFactory(MOTORCYCLE.getTypeId(), (type, id) -> new MotorcycleImpl(type, id));
        registry.registerClientFactory(CHAIN_PART.getTypeId(), (type, id) -> new VxChainPartRigidBody(type, id));
        registry.registerClientFactory(BODY_PART.getTypeId(), (type, id) -> new VxBodyPartRigidBody(type, id));
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
        registry.registerClientRenderer(CHAIN_PART.getTypeId(), new VxChainPartRenderer());
        registry.registerClientRenderer(BODY_PART.getTypeId(), new VxRagdollBodyPartRenderer());
    }
}