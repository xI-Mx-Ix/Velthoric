/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.builtin.box;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EBodyType;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.object.client.VxRenderState;
import net.xmx.velthoric.physics.object.client.body.VxClientRigidBody;
import net.xmx.velthoric.physics.object.sync.VxDataAccessor;
import net.xmx.velthoric.physics.object.sync.VxDataSerializers;
import org.joml.Quaternionf;

import java.util.UUID;

/**
 * @author xI-Mx-Ix
 */
public class BoxClientRigidBody extends VxClientRigidBody {

    private static final VxDataAccessor<Vec3> DATA_HALF_EXTENTS = VxDataAccessor.create(BoxClientRigidBody.class, VxDataSerializers.VEC3);
    private static final VxDataAccessor<Integer> DATA_COLOR_ORDINAL = VxDataAccessor.create(BoxClientRigidBody.class, VxDataSerializers.INTEGER);

    public BoxClientRigidBody(UUID id, VxClientObjectManager manager, int dataStoreIndex, EBodyType objectType) {
        super(id, manager, dataStoreIndex, objectType);
    }

    @Override
    protected void defineSyncData() {
        this.synchronizedData.define(DATA_HALF_EXTENTS, new Vec3(0.5f, 0.5f, 0.5f));
        this.synchronizedData.define(DATA_COLOR_ORDINAL, BoxColor.RED.ordinal());
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, float partialTicks, int packedLight, VxRenderState renderState) {
        int colorOrdinal = this.getSyncData(DATA_COLOR_ORDINAL);
        BoxColor color = (colorOrdinal >= 0 && colorOrdinal < BoxColor.values().length) ? BoxColor.values()[colorOrdinal] : BoxColor.RED;
        BlockState blockState = color.getBlock().defaultBlockState();

        Vec3 halfExtents = this.getSyncData(DATA_HALF_EXTENTS);
        float hx = halfExtents.getX();
        float hy = halfExtents.getY();
        float hz = halfExtents.getZ();

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