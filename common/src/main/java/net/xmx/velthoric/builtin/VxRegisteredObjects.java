/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.builtin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.builtin.block.BlockRigidBody;
import net.xmx.velthoric.builtin.box.BoxClientRigidBody;
import net.xmx.velthoric.builtin.box.BoxRigidBody;
import net.xmx.velthoric.builtin.cloth.ClothClientSoftBody;
import net.xmx.velthoric.builtin.cloth.ClothSoftBody;
import net.xmx.velthoric.builtin.marble.MarbleClientRigidBody;
import net.xmx.velthoric.builtin.marble.MarbleRigidBody;
import net.xmx.velthoric.builtin.rope.RopeClientSoftBody;
import net.xmx.velthoric.builtin.rope.RopeSoftBody;
import net.xmx.velthoric.builtin.sphere.SphereClientRigidBody;
import net.xmx.velthoric.builtin.sphere.SphereRigidBody;
import net.xmx.velthoric.physics.object.VxObjectType;
import net.xmx.velthoric.physics.object.registry.VxObjectRegistry;
import net.xmx.velthoric.builtin.block.BlockClientRigidBody;

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

    public static void register() {
        var registry = VxObjectRegistry.getInstance();
        registry.register(BLOCK);
        registry.register(SPHERE);
        registry.register(BOX);
        registry.register(MARBLE);
        registry.register(CLOTH);
        registry.register(ROPE);
    }

    @Environment(EnvType.CLIENT)
    public static void registerClientFactories() {
        var registry = VxObjectRegistry.getInstance();
        registry.registerClientFactory(BLOCK.getTypeId(), BlockClientRigidBody::new);
        registry.registerClientFactory(SPHERE.getTypeId(), SphereClientRigidBody::new);
        registry.registerClientFactory(BOX.getTypeId(), BoxClientRigidBody::new);
        registry.registerClientFactory(MARBLE.getTypeId(), MarbleClientRigidBody::new);
        registry.registerClientFactory(CLOTH.getTypeId(), ClothClientSoftBody::new);
        registry.registerClientFactory(ROPE.getTypeId(), RopeClientSoftBody::new);
    }
}