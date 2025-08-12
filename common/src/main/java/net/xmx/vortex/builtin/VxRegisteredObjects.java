package net.xmx.vortex.builtin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.xmx.vortex.api.VortexAPI;
import net.xmx.vortex.builtin.block.BlockRenderer;
import net.xmx.vortex.builtin.block.BlockRigidPhysicsObject;
import net.xmx.vortex.builtin.box.BoxRenderer;
import net.xmx.vortex.builtin.box.BoxRigidPhysicsObject;
import net.xmx.vortex.builtin.cloth.ClothSoftBody;
import net.xmx.vortex.builtin.cloth.ClothSoftBodyRenderer;
import net.xmx.vortex.builtin.rope.RopeSoftBody;
import net.xmx.vortex.builtin.rope.RopeSoftBodyRenderer;
import net.xmx.vortex.builtin.sphere.SphereRenderer;
import net.xmx.vortex.builtin.sphere.SphereRigidPhysicsObject;
import net.xmx.vortex.physics.object.physicsobject.PhysicsObjectType;

public class VxRegisteredObjects {

    public static final PhysicsObjectType<BlockRigidPhysicsObject> BLOCK = PhysicsObjectType.Builder
            .create(BlockRigidPhysicsObject::new)
            .build("vortex:block_obj");

    public static final PhysicsObjectType<SphereRigidPhysicsObject> SPHERE = PhysicsObjectType.Builder
            .create(SphereRigidPhysicsObject::new)
            .build("vortex:sphere_obj");

    public static final PhysicsObjectType<BoxRigidPhysicsObject> BOX = PhysicsObjectType.Builder
            .create(BoxRigidPhysicsObject::new)
            .build("vortex:box_obj");

    public static final PhysicsObjectType<ClothSoftBody> CLOTH = PhysicsObjectType.Builder
            .create(ClothSoftBody::new)
            .build("vortex:cloth_obj");

    public static final PhysicsObjectType<RopeSoftBody> ROPE = PhysicsObjectType.Builder
            .create(RopeSoftBody::new)
            .build("vortex:rope_obj");

    public static void register() {
        var api = VortexAPI.getInstance();

        api.registerPhysicsObjectType(BLOCK);
        api.registerPhysicsObjectType(SPHERE);
        api.registerPhysicsObjectType(BOX);
        api.registerPhysicsObjectType(CLOTH);
        api.registerPhysicsObjectType(ROPE);
    }

    @Environment(EnvType.CLIENT)
    public static void registerClientRenderers() {
        var api = VortexAPI.getInstance();

        api.registerRigidRenderer(BLOCK.getTypeId(), BlockRenderer::new);
        api.registerRigidRenderer(SPHERE.getTypeId(), SphereRenderer::new);
        api.registerRigidRenderer(BOX.getTypeId(), BoxRenderer::new);
        api.registerSoftRenderer(CLOTH.getTypeId(), ClothSoftBodyRenderer::new);
        api.registerSoftRenderer(ROPE.getTypeId(), RopeSoftBodyRenderer::new);
    }
}