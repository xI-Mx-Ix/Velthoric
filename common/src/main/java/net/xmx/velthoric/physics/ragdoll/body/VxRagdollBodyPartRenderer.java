/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.ragdoll.body;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.physics.body.client.VxRenderState;
import net.xmx.velthoric.physics.body.client.body.renderer.VxRigidBodyRenderer;
import net.xmx.velthoric.physics.ragdoll.VxBodyPart;
import org.joml.Quaternionf;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Renders a {@link VxBodyPartRigidBody} by drawing the corresponding part of a humanoid model
 * with the correct skin. This renderer avoids spawning any client-side entities.
 *
 * @author xI-Mx-Ix
 */
public class VxRagdollBodyPartRenderer extends VxRigidBodyRenderer<VxBodyPartRigidBody> {

    private HumanoidModel<net.minecraft.world.entity.LivingEntity> model;
    private final Map<VxBodyPart, ModelPart> partMap = new EnumMap<>(VxBodyPart.class);
    private boolean isInitialized = false;

    /**
     * Constructs the renderer. Initialization of graphical resources is deferred until the first render call.
     */
    public VxRagdollBodyPartRenderer() {
        // Initialization is handled lazily in the render method to prevent crashes.
    }

    /**
     * Initializes the renderer's resources, such as the humanoid model and its parts.
     * This is called on the first render frame to ensure all necessary game assets are available.
     */
    private void initialize() {
        ModelPart root = Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER);
        this.model = new HumanoidModel<>(root);

        partMap.put(VxBodyPart.HEAD, model.head);
        partMap.put(VxBodyPart.TORSO, model.body);
        partMap.put(VxBodyPart.LEFT_ARM, model.leftArm);
        partMap.put(VxBodyPart.RIGHT_ARM, model.rightArm);
        partMap.put(VxBodyPart.LEFT_LEG, model.leftLeg);
        partMap.put(VxBodyPart.RIGHT_LEG, model.rightLeg);

        this.isInitialized = true;
    }

    @Override
    public void render(VxBodyPartRigidBody body, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, float partialTicks, int packedLight, VxRenderState renderState) {
        if (!isInitialized) {
            initialize();
        }

        VxBodyPart partType = body.getSyncData(VxBodyPartRigidBody.DATA_BODY_PART);
        ModelPart modelPart = partMap.get(partType);
        if (modelPart == null) return;

        poseStack.pushPose();

        // Apply physics transform
        var transform = renderState.transform;
        poseStack.translate(transform.getTranslation().x(), transform.getTranslation().y(), transform.getTranslation().z());
        poseStack.mulPose(new Quaternionf(transform.getRotation().getX(), transform.getRotation().getY(), transform.getRotation().getZ(), transform.getRotation().getW()));

        // Scale the model part to match the physics body's size.
        float scaleX = body.getSyncData(VxBodyPartRigidBody.DATA_HALF_EXTENTS).getX() * 2.0f / partType.getSize().getX();
        float scaleY = body.getSyncData(VxBodyPartRigidBody.DATA_HALF_EXTENTS).getY() * 2.0f / partType.getSize().getY();
        float scaleZ = body.getSyncData(VxBodyPartRigidBody.DATA_HALF_EXTENTS).getZ() * 2.0f / partType.getSize().getZ();
        poseStack.scale(scaleX, scaleY, scaleZ);

        // Invert Y and Z axes to align Minecraft's model coordinate system
        // with the physics (and OpenGL) coordinate system.
        poseStack.scale(1.0f, -1.0f, -1.0f);

        // This block aligns the visual center of the ModelPart with the origin of the physics body.
        // It counteracts the ModelPart's pivot point and the offset of the rendered geometry from that pivot.
        // All translations are converted from model units to meters (16 units = 1 meter).
        poseStack.translate(-modelPart.x / 16.0f, -modelPart.y / 16.0f, -modelPart.z / 16.0f);
        switch (partType) {
            case HEAD -> poseStack.translate(0.0, 4.0 / 16.0, 0.0);
            case TORSO, LEFT_LEG, RIGHT_LEG -> poseStack.translate(0.0, -6.0 / 16.0, 0.0);
            case LEFT_ARM -> poseStack.translate(-1.0 / 16.0, -4.0 / 16.0, 0.0);
            case RIGHT_ARM -> poseStack.translate(1.0 / 16.0, -4.0 / 16.0, 0.0);
        }

        ResourceLocation skinTexture = getSkinTexture(body);
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.entitySolid(skinTexture));

        // Render the specific model part
        modelPart.render(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY);

        poseStack.popPose();
    }

    /**
     * Retrieves the appropriate skin texture for the rigid body part.
     * It attempts to find a player skin using the provided UUID string.
     *
     * @param body The rigid body part being rendered.
     * @return The {@link ResourceLocation} of the skin texture.
     */
    private ResourceLocation getSkinTexture(VxBodyPartRigidBody body) {
        String skinIdStr = body.getSyncData(VxBodyPartRigidBody.DATA_SKIN_ID);
        if (skinIdStr == null || skinIdStr.isEmpty()) {
            return DefaultPlayerSkin.getDefaultTexture();
        }

        try {
            UUID playerUuid = UUID.fromString(skinIdStr);
            if (Minecraft.getInstance().getConnection() != null) {
                PlayerInfo playerInfo = Minecraft.getInstance().getConnection().getPlayerInfo(playerUuid);
                if (playerInfo != null) {
                    return playerInfo.getSkin().texture();
                }
            }
        } catch (IllegalArgumentException e) {
            // fallback for non-player entities
        }

        return DefaultPlayerSkin.getDefaultTexture();
    }
}