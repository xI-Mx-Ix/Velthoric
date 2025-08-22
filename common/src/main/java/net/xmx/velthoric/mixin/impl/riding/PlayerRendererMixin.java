package net.xmx.velthoric.mixin.impl.riding;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.xmx.velthoric.physics.object.client.ClientObjectDataManager;
import net.xmx.velthoric.physics.object.client.interpolation.InterpolationFrame;
import net.xmx.velthoric.physics.object.client.interpolation.RenderState;
import net.xmx.velthoric.physics.riding.RidingProxyEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {

    private static final RenderState velthoric_reusableRenderState = new RenderState();

    private float getFlipDegrees(AbstractClientPlayer player) {
        return 90.0F;
    }

    @Inject(method = "setupRotations", at = @At("HEAD"), cancellable = true)
    private void velthoric_setupPhysicsObjectRotations(AbstractClientPlayer player, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTicks, CallbackInfo ci) {
        if (!(player.getVehicle() instanceof RidingProxyEntity proxy)) {
            return;
        }

        proxy.getPhysicsObjectId().ifPresent(id -> {
            InterpolationFrame frame = ClientObjectDataManager.getInstance().getInterpolationFrame(id);
            if (frame == null || !frame.isInitialized) {
                return;
            }

            frame.interpolate(velthoric_reusableRenderState, partialTicks);
            RVec3 physPos = velthoric_reusableRenderState.transform.getTranslation();
            Quat physRotQuat = velthoric_reusableRenderState.transform.getRotation();

            Quaternionf physRotation = new Quaternionf(physRotQuat.getX(), physRotQuat.getY(), physRotQuat.getZ(), physRotQuat.getW());

            Vector3f rideOffset = new Vector3f(proxy.getRidePositionOffset());
            physRotation.transform(rideOffset);

            double playerX = physPos.x() + rideOffset.x();
            double playerY = physPos.y() + rideOffset.y();
            double playerZ = physPos.z() + rideOffset.z();

            double renderPlayerX = Mth.lerp(partialTicks, player.xOld, player.getX());
            double renderPlayerY = Mth.lerp(partialTicks, player.yOld, player.getY());
            double renderPlayerZ = Mth.lerp(partialTicks, player.zOld, player.getZ());

            poseStack.translate(playerX - renderPlayerX, playerY - renderPlayerY, playerZ - renderPlayerZ);

            poseStack.mulPose(physRotation);

            if (player.deathTime > 0) {
                float f = ((float)player.deathTime + partialTicks - 1.0F) / 20.0F * 1.6F;
                f = Mth.sqrt(f);
                if (f > 1.0F) {
                    f = 1.0F;
                }
                poseStack.mulPose(Axis.ZP.rotationDegrees(f * this.getFlipDegrees(player)));
            } else if (player.isAutoSpinAttack()) {
                poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F - player.getXRot()));
                poseStack.mulPose(Axis.YP.rotationDegrees(((float)player.tickCount + partialTicks) * -75.0F));
            } else {
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - rotationYaw));
            }

            ci.cancel();
        });
    }
}