/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.builtin.box;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.enumerate.EBodyType;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.object.client.VxRenderState;
import net.xmx.velthoric.physics.object.client.body.VxClientRigidBody;
import org.joml.Quaternionf;

import java.util.UUID;

public class BoxClientRigidBody extends VxClientRigidBody {

    private float hx = 0.5f, hy = 0.5f, hz = 0.5f;
    private BoxColor color = BoxColor.RED;

    public BoxClientRigidBody(UUID id, VxClientObjectManager manager, int dataStoreIndex, EBodyType objectType) {
        super(id, manager, dataStoreIndex, objectType);
    }

    @Override
    public void readSyncData(VxByteBuf buf) {
        this.hx = buf.readFloat();
        this.hy = buf.readFloat();
        this.hz = buf.readFloat();
        int colorOrdinal = buf.readInt();
        if (colorOrdinal >= 0 && colorOrdinal < BoxColor.values().length) {
            this.color = BoxColor.values()[colorOrdinal];
        }
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, float partialTicks, int packedLight, VxRenderState renderState) {
        BlockState blockState = color.getBlock().defaultBlockState();

        float fullWidth = hx * 2.0f;
        float fullHeight = hy * 2.0f;
        float fullDepth = hz * 2.0f;

        poseStack.pushPose();

        RVec3 renderPosition = renderState.transform.getTranslation();
        Quat renderRotation = renderState.transform.getRotation();
        poseStack.translate(renderPosition.x(), renderPosition.y(), renderPosition.z());
        poseStack.mulPose(new Quaternionf(renderRotation.getX(), renderRotation.getY(), renderRotation.getZ(), renderRotation.getW()));

        poseStack.translate(-hx, -hy, -hz);
        poseStack.scale(fullWidth, fullHeight, fullDepth);

        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                blockState,
                poseStack,
                bufferSource,
                packedLight,
                OverlayTexture.NO_OVERLAY
        );

        poseStack.popPose();
    }
}