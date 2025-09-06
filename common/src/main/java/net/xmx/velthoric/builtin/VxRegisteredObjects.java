package net.xmx.velthoric.builtin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.api.VelthoricAPI;
import net.xmx.velthoric.builtin.block.BlockRenderer;
import net.xmx.velthoric.builtin.block.BlockRigidPhysicsObject;
import net.xmx.velthoric.builtin.box.BoxRenderer;
import net.xmx.velthoric.builtin.box.BoxRigidPhysicsObject;
import net.xmx.velthoric.builtin.cloth.ClothSoftBody;
import net.xmx.velthoric.builtin.cloth.ClothSoftBodyRenderer;
import net.xmx.velthoric.builtin.rope.RopeSoftBody;
import net.xmx.velthoric.builtin.rope.RopeSoftBodyRenderer;
import net.xmx.velthoric.builtin.sphere.SphereRenderer;
import net.xmx.velthoric.builtin.sphere.SphereRigidPhysicsObject;
import net.xmx.velthoric.physics.object.VxObjectType;

public class VxRegisteredObjects {

    public static final VxObjectType<BlockRigidPhysicsObject> BLOCK = VxObjectType.Builder
            .create(BlockRigidPhysicsObject::new)
            .build(new ResourceLocation("velthoric", "block_obj"));

    public static final VxObjectType<SphereRigidPhysicsObject> SPHERE = VxObjectType.Builder
            .create(SphereRigidPhysicsObject::new)
            .build(new ResourceLocation("velthoric", "sphere_obj"));

    public static final VxObjectType<BoxRigidPhysicsObject> BOX = VxObjectType.Builder
            .create(BoxRigidPhysicsObject::new)
            .build(new ResourceLocation("velthoric", "box_obj"));

    public static final VxObjectType<ClothSoftBody> CLOTH = VxObjectType.Builder
            .create(ClothSoftBody::new)
            .build(new ResourceLocation("velthoric", "cloth_obj"));

    public static final VxObjectType<RopeSoftBody> ROPE = VxObjectType.Builder
            .create(RopeSoftBody::new)
            .build(new ResourceLocation("velthoric", "rope_obj"));

    public static void register() {
        var api = VelthoricAPI.getInstance();

        api.registerObjectType(BLOCK);
        api.registerObjectType(SPHERE);
        api.registerObjectType(BOX);
        api.registerObjectType(CLOTH);
        api.registerObjectType(ROPE);
    }

    @Environment(EnvType.CLIENT)
    public static void registerClientRenderers() {
        var api = VelthoricAPI.getInstance();

        api.registerRendererFactory(BLOCK.getTypeId(), BlockRenderer::new);
        api.registerRendererFactory(SPHERE.getTypeId(), SphereRenderer::new);
        api.registerRendererFactory(BOX.getTypeId(), BoxRenderer::new);
        api.registerRendererFactory(CLOTH.getTypeId(), ClothSoftBodyRenderer::new);
        api.registerRendererFactory(ROPE.getTypeId(), RopeSoftBodyRenderer::new);
    }
}