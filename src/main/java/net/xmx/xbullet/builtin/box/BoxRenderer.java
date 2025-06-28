package net.xmx.xbullet.builtin.box;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.Blocks;
import net.xmx.xbullet.builtin.sphere.SphereRigidPhysicsObject;
import net.xmx.xbullet.physics.object.rigidphysicsobject.RigidPhysicsObject;
import net.xmx.xbullet.physics.object.rigidphysicsobject.client.ClientRigidPhysicsObjectData;

public class BoxRenderer extends RigidPhysicsObject.Renderer {

    @Override
    public void render(ClientRigidPhysicsObjectData data, PoseStack poseStack, MultiBufferSource bufferSource, float partialTicks, int packedLight) {

        var nbt = data.getSyncedNbtData();
        String typeIdentifier = nbt.getString("objectTypeIdentifier");

        if (!BoxRigidPhysicsObject.TYPE_IDENTIFIER.equals(typeIdentifier)) {
            return;
        }

        poseStack.pushPose();

        poseStack.translate(-0.5f, -0.5f, -0.5f);

        var dispatcher = Minecraft.getInstance().getBlockRenderer();

        dispatcher.renderSingleBlock(Blocks.RED_CONCRETE.defaultBlockState(), poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY);

        poseStack.popPose();
    }
}