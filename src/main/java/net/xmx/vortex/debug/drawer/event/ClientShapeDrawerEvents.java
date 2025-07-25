package net.xmx.vortex.debug.drawer.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.xmx.vortex.debug.drawer.client.ClientShapeDrawer;

public final class ClientShapeDrawerEvents {

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            if (ClientShapeDrawer.ENABLED) {
                var poseStack = event.getPoseStack();
                var mc = Minecraft.getInstance();
                var vertexConsumer = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());
                Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();

                ClientShapeDrawer.getInstance().render(poseStack, vertexConsumer, cameraPos);

                mc.renderBuffers().bufferSource().endBatch(RenderType.lines());
            }
        }
    }
}
