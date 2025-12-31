/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.builtin.drivable.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.xmx.velthoric.physics.vehicle.part.VxPartRenderer;
import net.xmx.velthoric.physics.vehicle.part.impl.VxVehicleSeat;
import org.joml.Vector3f;

/**
 * Handles the rendering of vehicle seats.
 * Renders an Oak Stair to visualize the seat location.
 *
 * @author xI-Mx-Ix
 */
@Environment(EnvType.CLIENT)
public class VxSeatRenderer extends VxPartRenderer<VxVehicleSeat> {

    private static final BlockState SEAT_STATE = Blocks.OAK_STAIRS.defaultBlockState();

    @Override
    public void render(VxVehicleSeat part, PoseStack poseStack, MultiBufferSource bufferSource, float partialTicks, int packedLight) {
        poseStack.pushPose();

        // Move to the local part position (Vehicle Relative)
        Vector3f localPos = part.getLocalPosition();
        poseStack.translate(localPos.x, localPos.y, localPos.z);
        
        // Scale and center the stair to look somewhat like a seat
        // Typically seat positions are at the base, so we might need to adjust slightly
        poseStack.translate(-0.5, 0.0, -0.5); 
        
        // Render the block
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(SEAT_STATE, poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY);
        
        poseStack.popPose();
    }
}