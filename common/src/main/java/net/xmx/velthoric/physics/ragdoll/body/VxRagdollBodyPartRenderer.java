/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.ragdoll.body;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.xmx.velthoric.physics.body.client.VxRenderState;
import net.xmx.velthoric.physics.body.client.body.renderer.VxRigidBodyRenderer;
import net.xmx.velthoric.physics.ragdoll.VxBodyPart;
import org.joml.Quaternionf;

import java.util.UUID;

/**
 * Renders a {@link VxBodyPartRigidBody} using the Minecraft {@link PlayerModel}.
 * <p>
 * This renderer supports:
 * <ul>
 *     <li>Dynamic switching between "Steve" (wide) and "Alex" (slim) models based on skin metadata.</li>
 *     <li>Rendering of secondary skin layers (hat, jacket, sleeves, pants).</li>
 *     <li>Correct transparency handling for the outer skin layer.</li>
 * </ul>
 *
 * @author xI-Mx-Ix
 */
public class VxRagdollBodyPartRenderer extends VxRigidBodyRenderer<VxBodyPartRigidBody> {

    /**
     * The model instance for the standard "Steve" skin layout (wide arms).
     */
    private PlayerModel<LivingEntity> modelWide;

    /**
     * The model instance for the "Alex" skin layout (slim arms).
     */
    private PlayerModel<LivingEntity> modelSlim;

    private boolean isInitialized = false;

    /**
     * Constructs the renderer. Model baking is deferred to the first render cycle.
     */
    public VxRagdollBodyPartRenderer() {
        // Initialization is handled lazily to ensure the EntityModelSet is ready.
    }

    /**
     * Initializes the player models by baking the geometry layers.
     */
    private void initialize() {
        var entityModels = Minecraft.getInstance().getEntityModels();

        // Bake both model variants to support all player skins
        this.modelWide = new PlayerModel<>(entityModels.bakeLayer(ModelLayers.PLAYER), false);
        this.modelSlim = new PlayerModel<>(entityModels.bakeLayer(ModelLayers.PLAYER_SLIM), true);

        this.isInitialized = true;
    }

    @Override
    public void render(VxBodyPartRigidBody body, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, float partialTicks, int packedLight, VxRenderState renderState) {
        if (!isInitialized) {
            initialize();
        }

        VxBodyPart partType = body.get(VxBodyPartRigidBody.DATA_BODY_PART);
        SkinData skinData = resolveSkinData(body);

        // Select the correct model geometry based on the skin type (Slim vs Wide)
        PlayerModel<LivingEntity> activeModel = skinData.isSlim ? this.modelSlim : this.modelWide;

        poseStack.pushPose();

        // 1. Apply Physics Transform (Rotation from Jolt Physics)
        var transform = renderState.transform;
        poseStack.mulPose(new Quaternionf(transform.getRotation().getX(), transform.getRotation().getY(), transform.getRotation().getZ(), transform.getRotation().getW()));

        // 2. Scale visual model to match physics bounds
        // We calculate the scale factor required to stretch the model part to fill the rigid body's dimensions.
        float physicsHalfX = body.get(VxBodyPartRigidBody.DATA_HALF_EXTENTS).getX();
        float physicsHalfY = body.get(VxBodyPartRigidBody.DATA_HALF_EXTENTS).getY();
        float physicsHalfZ = body.get(VxBodyPartRigidBody.DATA_HALF_EXTENTS).getZ();

        float scaleX = physicsHalfX * 2.0f / partType.getSize().getX();
        float scaleY = physicsHalfY * 2.0f / partType.getSize().getY();
        float scaleZ = physicsHalfZ * 2.0f / partType.getSize().getZ();

        poseStack.scale(scaleX, scaleY, scaleZ);

        // 3. Coordinate System Correction
        // Jolt/OpenGL uses Y-Up, Minecraft Models use Y-Down.
        poseStack.scale(1.0f, -1.0f, -1.0f);

        // 4. Center Alignment
        // Align the specific body part so its pivot matches the rigid body's center.
        applyPartOffset(poseStack, partType);

        // 5. Render the part and its overlay (layer)
        // We use entityTranslucent to support transparency in the outer skin layer (e.g., glasses, open jackets).
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(skinData.texture));
        renderBodyPartLayers(activeModel, partType, poseStack, consumer, packedLight);

        poseStack.popPose();
    }

    /**
     * Renders the primary body part and its corresponding outer layer (skin overlay).
     *
     * @param model      The player model (Slim or Wide) to use.
     * @param partType   The physics body part being rendered.
     * @param poseStack  The current matrix stack.
     * @param consumer   The vertex consumer for drawing.
     * @param packedLight The lighting value.
     */
    private void renderBodyPartLayers(PlayerModel<LivingEntity> model, VxBodyPart partType, PoseStack poseStack, VertexConsumer consumer, int packedLight) {
        // We render both the base layer (flesh) and the outer layer (clothing/armor)
        // using the same transform.
        switch (partType) {
            case HEAD -> {
                // Head + Hat Layer
                renderPart(model.head, poseStack, consumer, packedLight);
                renderPart(model.hat, poseStack, consumer, packedLight);
            }
            case TORSO -> {
                // Body + Jacket
                renderPart(model.body, poseStack, consumer, packedLight);
                renderPart(model.jacket, poseStack, consumer, packedLight);
            }
            case LEFT_ARM -> {
                // Left Arm + Left Sleeve
                renderPart(model.leftArm, poseStack, consumer, packedLight);
                renderPart(model.leftSleeve, poseStack, consumer, packedLight);
            }
            case RIGHT_ARM -> {
                // Right Arm + Right Sleeve
                renderPart(model.rightArm, poseStack, consumer, packedLight);
                renderPart(model.rightSleeve, poseStack, consumer, packedLight);
            }
            case LEFT_LEG -> {
                // Left Leg + Left Pants
                renderPart(model.leftLeg, poseStack, consumer, packedLight);
                renderPart(model.leftPants, poseStack, consumer, packedLight);
            }
            case RIGHT_LEG -> {
                // Right Leg + Right Pants
                renderPart(model.rightLeg, poseStack, consumer, packedLight);
                renderPart(model.rightPants, poseStack, consumer, packedLight);
            }
        }
    }

    /**
     * Helper to render a specific ModelPart.
     */
    private void renderPart(ModelPart part, PoseStack poseStack, VertexConsumer consumer, int packedLight) {
        part.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
    }

    /**
     * Applies translations to align the model part's visual center with the physics body origin.
     * <p>
     * Minecraft ModelParts are positioned relative to a global origin (0, 24, 0).
     * We must translate them back to local space (meters).
     *
     * @param poseStack The pose stack to translate.
     * @param partType  The body part type to align.
     */
    private void applyPartOffset(PoseStack poseStack, VxBodyPart partType) {
        // Standard translation to move model from pixel coordinates to meters (1/16 scale).
        // Specific offsets are based on the vanilla PlayerModel pivot points.

        // Note: We use fixed offsets based on the 'Wide' model structure, as the rigid bodies
        // are centered. The 'Slim' model shares the same pivot points for arms.
        switch (partType) {
            case HEAD -> {
                // Head pivot is at y=0 (in model space), usually rendered at top.
                // Physics body center is in the middle of the head box (8x8x8).
                poseStack.translate(0.0, 4.0 / 16.0, 0.0);
            }
            case TORSO -> {
                // Body pivot is at y=0.
                poseStack.translate(0.0, -6.0 / 16.0, 0.0);
            }
            case LEFT_LEG, RIGHT_LEG -> {
                // Legs pivot at y=12.
                poseStack.translate(0.0, -6.0 / 16.0, 0.0);
                // Note: The specific X offsets (left/right) are handled by the ModelPart's own 'x'
                // coordinate which we subtract below.
            }
            case LEFT_ARM -> {
                // Arm pivot at y=2.
                poseStack.translate(-1.0 / 16.0, -4.0 / 16.0, 0.0);
            }
            case RIGHT_ARM -> {
                poseStack.translate(1.0 / 16.0, -4.0 / 16.0, 0.0);
            }
        }

        // Finally, subtract the ModelPart's inherent position to bring it to origin.
        // We use the 'Wide' model as reference for centering logic.
        ModelPart referencePart = getReferencePart(partType);
        if (referencePart != null) {
            poseStack.translate(-referencePart.x / 16.0f, -referencePart.y / 16.0f, -referencePart.z / 16.0f);
        }
    }

    /**
     * Retrieves the reference ModelPart from the Wide model to calculate initial offsets.
     */
    private ModelPart getReferencePart(VxBodyPart partType) {
        return switch (partType) {
            case HEAD -> modelWide.head;
            case TORSO -> modelWide.body;
            case LEFT_ARM -> modelWide.leftArm;
            case RIGHT_ARM -> modelWide.rightArm;
            case LEFT_LEG -> modelWide.leftLeg;
            case RIGHT_LEG -> modelWide.rightLeg;
        };
    }

    /**
     * Resolves the skin texture and model type (Slim vs Wide) for the given body.
     *
     * @param body The rigid body containing the skin ID (UUID).
     * @return A {@link SkinData} record containing the texture and model type.
     */
    private SkinData resolveSkinData(VxBodyPartRigidBody body) {
        String skinIdStr = body.get(VxBodyPartRigidBody.DATA_SKIN_ID);

        if (skinIdStr != null && !skinIdStr.isEmpty()) {
            try {
                UUID playerUuid = UUID.fromString(skinIdStr);
                if (Minecraft.getInstance().getConnection() != null) {
                    PlayerInfo playerInfo = Minecraft.getInstance().getConnection().getPlayerInfo(playerUuid);
                    if (playerInfo != null) {
                        PlayerSkin skin = playerInfo.getSkin();
                        boolean isSlim = skin.model() == PlayerSkin.Model.SLIM;
                        return new SkinData(skin.texture(), isSlim);
                    }
                }
            } catch (IllegalArgumentException ignored) {
                // Invalid UUID format
            }
        }

        // Fallback to default Steve skin
        return new SkinData(DefaultPlayerSkin.getDefaultTexture(), false);
    }

    /**
     * Simple container for resolved skin information.
     */
    private record SkinData(ResourceLocation texture, boolean isSlim) {}
}