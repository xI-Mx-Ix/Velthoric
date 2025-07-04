package net.xmx.xbullet.test;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.xmx.xbullet.model.objmodel.OBJModel;

public class ExampleCarRenderer extends EntityRenderer<ExampleCarEntity> {

    private static final ResourceLocation MODEL_LOCATION = ResourceLocation.tryBuild("xbullet", "obj/acura_mdx_yd1.obj");
    private static final ResourceLocation TEXTURE_LOCATION = ResourceLocation.tryBuild("xbullet", "obj/acura_mdx_yd1.png");
    private OBJModel carModel;

    private boolean isInitialized = false;

    public ExampleCarRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    private void initialize() {
        if (isInitialized) return;
        try {
            carModel = new OBJModel.Builder(MODEL_LOCATION)
                    .withDefaultTexture(TEXTURE_LOCATION)
                    .build();
        } catch (Exception e) {
            System.err.println("Konnte das Auto-Modell nicht laden: " + MODEL_LOCATION);
            e.printStackTrace();
            carModel = null;
        }
        isInitialized = true;
    }

    @Override
    public void render(ExampleCarEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        initialize();

        if (carModel == null) {
            return;
        }

        poseStack.pushPose();

        poseStack.translate(0.0D, 0.01D, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(-entityYaw));

        carModel.render(poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);

        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(ExampleCarEntity entity) {
        return TEXTURE_LOCATION;
    }

    public void destroy() {
        if (carModel != null) {
            carModel.close();
        }
    }
}