package net.xmx.xbullet.builtin;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.xmx.xbullet.api.XBulletAPI;
import net.xmx.xbullet.builtin.block.BlockPhysicsProperties;
import net.xmx.xbullet.builtin.block.BlockRenderer;
import net.xmx.xbullet.builtin.block.BlockRigidPhysicsObject;
import net.xmx.xbullet.builtin.box.BoxPhysicsProperties;
import net.xmx.xbullet.builtin.box.BoxRenderer;
import net.xmx.xbullet.builtin.box.BoxRigidPhysicsObject;
import net.xmx.xbullet.builtin.cloth.ClothPhysicsProperties;
import net.xmx.xbullet.builtin.cloth.ClothSoftBody;
import net.xmx.xbullet.builtin.cloth.ClothSoftBodyRenderer;
import net.xmx.xbullet.builtin.rope.RopePhysicsProperties;
import net.xmx.xbullet.builtin.rope.RopeSoftBody;
import net.xmx.xbullet.builtin.rope.RopeSoftBodyRenderer;
import net.xmx.xbullet.builtin.sphere.SpherePhysicsProperties;
import net.xmx.xbullet.builtin.sphere.SphereRenderer;
import net.xmx.xbullet.builtin.sphere.SphereRigidPhysicsObject;
import net.xmx.xbullet.physics.object.global.physicsobject.EObjectType;

public class BuiltInPhysicsRegistry {

    public static void register() {
        var api = XBulletAPI.getInstance().objects();

        api.registerObjectType(BlockRigidPhysicsObject.TYPE_IDENTIFIER, EObjectType.RIGID_BODY, BlockPhysicsProperties.blockProperties, BlockRigidPhysicsObject.class);
        api.registerObjectType(SphereRigidPhysicsObject.TYPE_IDENTIFIER, EObjectType.RIGID_BODY, SpherePhysicsProperties.sphereProperties, SphereRigidPhysicsObject.class);
        api.registerObjectType(BoxRigidPhysicsObject.TYPE_IDENTIFIER, EObjectType.RIGID_BODY, BoxPhysicsProperties.boxProperties, BoxRigidPhysicsObject.class);

        api.registerObjectType(ClothSoftBody.TYPE_IDENTIFIER, EObjectType.SOFT_BODY, ClothPhysicsProperties.clothProperties, ClothSoftBody.class);

        api.registerObjectType(RopeSoftBody.TYPE_IDENTIFIER, EObjectType.SOFT_BODY, RopePhysicsProperties.ropeProperties, RopeSoftBody.class);
    }

    @OnlyIn(Dist.CLIENT)
    public static void registerClientRenderers() {
        var api = XBulletAPI.getInstance().objects();

        api.registerRigidRenderer(BlockRigidPhysicsObject.TYPE_IDENTIFIER, BlockRenderer::new);
        api.registerRigidRenderer(SphereRigidPhysicsObject.TYPE_IDENTIFIER, SphereRenderer::new);
        api.registerRigidRenderer(BoxRigidPhysicsObject.TYPE_IDENTIFIER, BoxRenderer::new);

        api.registerSoftRenderer(ClothSoftBody.TYPE_IDENTIFIER, ClothSoftBodyRenderer::new);

        api.registerSoftRenderer(RopeSoftBody.TYPE_IDENTIFIER, RopeSoftBodyRenderer::new);
    }
}