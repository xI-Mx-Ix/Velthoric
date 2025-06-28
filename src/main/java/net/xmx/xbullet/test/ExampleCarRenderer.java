package net.xmx.xbullet.test;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class ExampleCarRenderer extends EntityRenderer<ExampleCarEntity> {

    private static final ResourceLocation MODEL_LOCATION = new ResourceLocation("xbullet", "obj/acura_mdx_yd1.obj");

    private static final ResourceLocation TEXTURE_LOCATION = new ResourceLocation("xbullet", "obj/acura_mdx_yd1.png");

    public ExampleCarRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(ExampleCarEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {

        poseStack.pushPose();

        poseStack.translate(0.0D, 0.01D, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(-entityYaw));
        poseStack.scale(1.0F, 1.0F, 1.0F);

        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(ExampleCarEntity entity) {
        return TEXTURE_LOCATION;
    }
}