package net.xmx.vortex.builtin;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.xmx.vortex.api.VortexAPI;
import net.xmx.vortex.builtin.block.BlockPhysicsProperties;
import net.xmx.vortex.builtin.block.BlockRenderer;
import net.xmx.vortex.builtin.block.BlockRigidPhysicsObject;
import net.xmx.vortex.builtin.box.BoxPhysicsProperties;
import net.xmx.vortex.builtin.box.BoxRenderer;
import net.xmx.vortex.builtin.box.BoxRigidPhysicsObject;
import net.xmx.vortex.builtin.cloth.ClothPhysicsProperties;
import net.xmx.vortex.builtin.cloth.ClothSoftBody;
import net.xmx.vortex.builtin.cloth.ClothSoftBodyRenderer;
import net.xmx.vortex.builtin.rope.RopePhysicsProperties;
import net.xmx.vortex.builtin.rope.RopeSoftBody;
import net.xmx.vortex.builtin.rope.RopeSoftBodyRenderer;
import net.xmx.vortex.builtin.sphere.SpherePhysicsProperties;
import net.xmx.vortex.builtin.sphere.SphereRenderer;
import net.xmx.vortex.builtin.sphere.SphereRigidPhysicsObject;
import net.xmx.vortex.physics.object.physicsobject.EObjectType;

public class BuiltInPhysicsRegistry {

    public static void register() {
        var api = VortexAPI.getInstance().objects();

        api.registerObjectType(BlockRigidPhysicsObject.TYPE_IDENTIFIER, EObjectType.RIGID_BODY, BlockPhysicsProperties.blockProperties, BlockRigidPhysicsObject.class);
        api.registerObjectType(SphereRigidPhysicsObject.TYPE_IDENTIFIER, EObjectType.RIGID_BODY, SpherePhysicsProperties.sphereProperties, SphereRigidPhysicsObject.class);
        api.registerObjectType(BoxRigidPhysicsObject.TYPE_IDENTIFIER, EObjectType.RIGID_BODY, BoxPhysicsProperties.boxProperties, BoxRigidPhysicsObject.class);

        api.registerObjectType(ClothSoftBody.TYPE_IDENTIFIER, EObjectType.SOFT_BODY, ClothPhysicsProperties.clothProperties, ClothSoftBody.class);

        api.registerObjectType(RopeSoftBody.TYPE_IDENTIFIER, EObjectType.SOFT_BODY, RopePhysicsProperties.ropeProperties, RopeSoftBody.class);
    }

    @OnlyIn(Dist.CLIENT)
    public static void registerClientRenderers() {
        var api = VortexAPI.getInstance().objects();

        api.registerRigidRenderer(BlockRigidPhysicsObject.TYPE_IDENTIFIER, BlockRenderer::new);
        api.registerRigidRenderer(SphereRigidPhysicsObject.TYPE_IDENTIFIER, SphereRenderer::new);
        api.registerRigidRenderer(BoxRigidPhysicsObject.TYPE_IDENTIFIER, BoxRenderer::new);

        api.registerSoftRenderer(ClothSoftBody.TYPE_IDENTIFIER, ClothSoftBodyRenderer::new);

        api.registerSoftRenderer(RopeSoftBody.TYPE_IDENTIFIER, RopeSoftBodyRenderer::new);
    }
}