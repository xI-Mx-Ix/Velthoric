package net.xmx.velthoric.builtin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
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
            .build("velthoric:block_obj");

    public static final VxObjectType<SphereRigidPhysicsObject> SPHERE = VxObjectType.Builder
            .create(SphereRigidPhysicsObject::new)
            .build("velthoric:sphere_obj");

    public static final VxObjectType<BoxRigidPhysicsObject> BOX = VxObjectType.Builder
            .create(BoxRigidPhysicsObject::new)
            .build("velthoric:box_obj");

    public static final VxObjectType<ClothSoftBody> CLOTH = VxObjectType.Builder
            .create(ClothSoftBody::new)
            .build("velthoric:cloth_obj");

    public static final VxObjectType<RopeSoftBody> ROPE = VxObjectType.Builder
            .create(RopeSoftBody::new)
            .build("velthoric:rope_obj");

    public static void register() {
        var api = VelthoricAPI.getInstance();

        api.registerPhysicsObjectType(BLOCK);
        api.registerPhysicsObjectType(SPHERE);
        api.registerPhysicsObjectType(BOX);
        api.registerPhysicsObjectType(CLOTH);
        api.registerPhysicsObjectType(ROPE);
    }

    @Environment(EnvType.CLIENT)
    public static void registerClientRenderers() {
        var api = VelthoricAPI.getInstance();

        api.registerRigidRenderer(BLOCK.getTypeId(), BlockRenderer::new);
        api.registerRigidRenderer(SPHERE.getTypeId(), SphereRenderer::new);
        api.registerRigidRenderer(BOX.getTypeId(), BoxRenderer::new);
        api.registerSoftRenderer(CLOTH.getTypeId(), ClothSoftBodyRenderer::new);
        api.registerSoftRenderer(ROPE.getTypeId(), RopeSoftBodyRenderer::new);
    }
}