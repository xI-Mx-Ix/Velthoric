/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.ship.body;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.xmx.velthoric.physics.object.client.VxRenderState;
import net.xmx.velthoric.physics.object.type.VxRigidBody;
import org.joml.Quaternionf;

import java.nio.ByteBuffer;
import java.util.UUID;

public class VxShipBodyRenderer implements VxRigidBody.Renderer {

    @Override
    public void render(UUID id, VxRenderState renderState, ByteBuffer customData, PoseStack poseStack, MultiBufferSource bufferSource, float partialTick, int packedLight) {

        AABB box = new AABB(0, 0, 0, 1, 1, 1);

        poseStack.pushPose();

        RVec3 position = renderState.transform.getTranslation();
        Quat rotation = renderState.transform.getRotation();

        poseStack.translate(position.xx(), position.yy(), position.zz());

        poseStack.mulPose(new Quaternionf(rotation.getX(), rotation.getY(), rotation.getZ(), rotation.getW()));

        LevelRenderer.renderLineBox(
                poseStack,
                bufferSource.getBuffer(RenderType.lines()),
                box,
                1.0f, 0.0f, 0.0f, 1.0f
        );

        poseStack.popPose();
    }
}