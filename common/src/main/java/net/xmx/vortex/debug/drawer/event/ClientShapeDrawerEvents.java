package net.xmx.vortex.debug.drawer.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.phys.Vec3;
import net.xmx.vortex.debug.drawer.client.ClientShapeDrawer;
import net.xmx.vortex.event.api.VxRenderEvent;
import net.xmx.vortex.mixin.util.IEntityRenderDispatcherAccessor;

public final class ClientShapeDrawerEvents {

    public static void registerEvents() {
        VxRenderEvent.ClientRenderLevelStageEvent.EVENT.register(ClientShapeDrawerEvents::onRenderLevelStage);
    }

    public static void onRenderLevelStage(VxRenderEvent.ClientRenderLevelStageEvent event) {
        if (event.getStage() == VxRenderEvent.ClientRenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            if (ClientShapeDrawer.ENABLED) {
                var poseStack = event.getPoseStack();
                var mc = Minecraft.getInstance();
                var vertexConsumer = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());
                Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();

                EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
                boolean renderHitBoxes = ((IEntityRenderDispatcherAccessor) dispatcher).isRenderHitBoxes();

                if (renderHitBoxes) {
                    ClientShapeDrawer.getInstance().render(poseStack, vertexConsumer, cameraPos);
                }

                mc.renderBuffers().bufferSource().endBatch(RenderType.lines());
            }
        }
    }
}
