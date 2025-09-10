package net.xmx.velthoric.builtin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.builtin.block.BlockRenderer;
import net.xmx.velthoric.builtin.block.BlockRigidBody;
import net.xmx.velthoric.builtin.box.BoxRenderer;
import net.xmx.velthoric.builtin.box.BoxRigidBody;
import net.xmx.velthoric.builtin.cloth.ClothSoftBody;
import net.xmx.velthoric.builtin.cloth.ClothSoftBodyRenderer;
import net.xmx.velthoric.builtin.marble.MarbleRenderer;
import net.xmx.velthoric.builtin.marble.MarbleRigidBody;
import net.xmx.velthoric.builtin.rope.RopeSoftBody;
import net.xmx.velthoric.builtin.rope.RopeSoftBodyRenderer;
import net.xmx.velthoric.builtin.sphere.SphereRenderer;
import net.xmx.velthoric.builtin.sphere.SphereRigidBody;
import net.xmx.velthoric.physics.object.VxObjectType;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.object.manager.registry.VxObjectRegistry;

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
    public static void registerClientRenderers() {
        var registry = VxClientObjectManager.getInstance().getRegistry();

        registry.registerRendererFactory(BLOCK.getTypeId(), BlockRenderer::new);
        registry.registerRendererFactory(SPHERE.getTypeId(), SphereRenderer::new);
        registry.registerRendererFactory(BOX.getTypeId(), BoxRenderer::new);
        registry.registerRendererFactory(MARBLE.getTypeId(), MarbleRenderer::new);
        registry.registerRendererFactory(CLOTH.getTypeId(), ClothSoftBodyRenderer::new);
        registry.registerRendererFactory(ROPE.getTypeId(), RopeSoftBodyRenderer::new);
    }
}