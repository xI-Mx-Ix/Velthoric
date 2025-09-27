/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.riding.render.bounds;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.xmx.velthoric.physics.object.client.VxClientObjectDataStore;
import net.xmx.velthoric.physics.object.client.VxClientObjectInterpolator;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.riding.VxRidingProxyEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author xI-Mx-Ix
 */
@Mixin(PlayerRenderer.class)
public abstract class MixinPlayerRenderer_TransformPlayer {

    private static final RVec3 velthoric_interpolatedPosition_pr = new RVec3();
    private static final Quat velthoric_interpolatedRotation_pr = new Quat();

    @Unique
    private float velthoric$getFlipDegrees(AbstractClientPlayer player) {
        return 90.0F;
    }

    @Inject(method = "setupRotations", at = @At("HEAD"), cancellable = true)
    private void velthoric_setupPhysicsObjectRotations(AbstractClientPlayer player, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTicks, CallbackInfo ci) {
        if (!(player.getVehicle() instanceof VxRidingProxyEntity proxy)) {
            return;
        }

        proxy.getPhysicsObjectId().ifPresent(id -> {
            VxClientObjectManager manager = VxClientObjectManager.getInstance();
            VxClientObjectDataStore store = manager.getStore();
            VxClientObjectInterpolator interpolator = manager.getInterpolator();
            Integer index = store.getIndexForId(id);

            if (index == null || !store.render_isInitialized[index]) {
                return;
            }

            interpolator.interpolateFrame(store, index, partialTicks, velthoric_interpolatedPosition_pr, velthoric_interpolatedRotation_pr);

            Quaternionf physRotation = new Quaternionf(
                    velthoric_interpolatedRotation_pr.getX(),
                    velthoric_interpolatedRotation_pr.getY(),
                    velthoric_interpolatedRotation_pr.getZ(),
                    velthoric_interpolatedRotation_pr.getW()
            );

            Vector3f rideOffset = new Vector3f(proxy.getRidePositionOffset());
            physRotation.transform(rideOffset);

            double playerX = velthoric_interpolatedPosition_pr.xx() + rideOffset.x();
            double playerY = velthoric_interpolatedPosition_pr.yy() + rideOffset.y();
            double playerZ = velthoric_interpolatedPosition_pr.zz() + rideOffset.z();

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
                poseStack.mulPose(Axis.ZP.rotationDegrees(f * this.velthoric$getFlipDegrees(player)));
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