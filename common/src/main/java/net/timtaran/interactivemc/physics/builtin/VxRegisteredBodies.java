/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.builtin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;
import net.timtaran.interactivemc.physics.builtin.block.BlockRenderer;
import net.timtaran.interactivemc.physics.builtin.block.BlockRigidBody;
import net.timtaran.interactivemc.physics.builtin.box.BoxRenderer;
import net.timtaran.interactivemc.physics.builtin.box.BoxRigidBody;
import net.timtaran.interactivemc.physics.builtin.marble.MarbleRenderer;
import net.timtaran.interactivemc.physics.builtin.marble.MarbleRigidBody;
import net.timtaran.interactivemc.physics.item.chaincreator.body.VxChainPartRenderer;
import net.timtaran.interactivemc.physics.item.chaincreator.body.VxChainPartRigidBody;
import net.timtaran.interactivemc.physics.physics.body.registry.VxBodyType;
import net.timtaran.interactivemc.physics.physics.body.registry.VxBodyRegistry;
import net.timtaran.interactivemc.physics.physics.ragdoll.body.VxBodyPartRigidBody;
import net.timtaran.interactivemc.physics.physics.ragdoll.body.VxRagdollBodyPartRenderer;

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
            .build(ResourceLocation.tryBuild("velthoric", "block"));

    public static final VxBodyType<BoxRigidBody> BOX = VxBodyType.Builder
            .<BoxRigidBody>create(BoxRigidBody::new)
            .build(ResourceLocation.tryBuild("velthoric", "box"));

    public static final VxBodyType<MarbleRigidBody> MARBLE = VxBodyType.Builder
            .<MarbleRigidBody>create(MarbleRigidBody::new)
            .build(ResourceLocation.tryBuild("velthoric", "marble"));

    public static final VxBodyType<VxChainPartRigidBody> CHAIN_PART = VxBodyType.Builder
            .<VxChainPartRigidBody>create(VxChainPartRigidBody::new)
            .noSummon()
            .build(ResourceLocation.tryBuild("velthoric", "chain_part"));

    public static final VxBodyType<VxBodyPartRigidBody> BODY_PART = VxBodyType.Builder
            .<VxBodyPartRigidBody>create(VxBodyPartRigidBody::new)
            .noSummon()
            .build(ResourceLocation.tryBuild("velthoric", "body_part"));

    /**
     * Registers all server-side physics body types. This should be called
     * during the server initialization phase.
     */
    public static void register() {
        var registry = VxBodyRegistry.getInstance();
        registry.register(BLOCK);
        registry.register(BOX);
        registry.register(MARBLE);
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
        registry.registerClientFactory(BLOCK.getTypeId(), (type, id) -> new BlockRigidBody((VxBodyType<BlockRigidBody>) type, id));
        registry.registerClientFactory(BOX.getTypeId(), (type, id) -> new BoxRigidBody((VxBodyType<BoxRigidBody>) type, id));
        registry.registerClientFactory(MARBLE.getTypeId(), (type, id) -> new MarbleRigidBody((VxBodyType<MarbleRigidBody>) type, id));
        registry.registerClientFactory(CHAIN_PART.getTypeId(), (type, id) -> new VxChainPartRigidBody((VxBodyType<VxChainPartRigidBody>) type, id));
        registry.registerClientFactory(BODY_PART.getTypeId(), (type, id) -> new VxBodyPartRigidBody((VxBodyType<VxBodyPartRigidBody>) type, id));
    }

    /**
     * Registers all client-side renderers for physics bodies.
     * This must be called on the client after factories are registered.
     */
    @Environment(EnvType.CLIENT)
    public static void registerClientRenderers() {
        var registry = VxBodyRegistry.getInstance();
        registry.registerClientRenderer(BLOCK.getTypeId(), new BlockRenderer());
        registry.registerClientRenderer(BOX.getTypeId(), new BoxRenderer());
        registry.registerClientRenderer(MARBLE.getTypeId(), new MarbleRenderer());
        registry.registerClientRenderer(CHAIN_PART.getTypeId(), new VxChainPartRenderer());
        registry.registerClientRenderer(BODY_PART.getTypeId(), new VxRagdollBodyPartRenderer());
    }
}