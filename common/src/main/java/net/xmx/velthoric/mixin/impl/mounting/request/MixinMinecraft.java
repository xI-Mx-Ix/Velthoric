/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.mounting.request;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.math.VxOBB;
import net.xmx.velthoric.network.VxPacketHandler;
import net.xmx.velthoric.physics.body.client.VxClientBodyManager;
import net.xmx.velthoric.physics.body.client.VxRenderState;
import net.xmx.velthoric.physics.mounting.manager.VxClientMountingManager;
import net.xmx.velthoric.physics.mounting.request.C2SRequestMountPacket;
import net.xmx.velthoric.physics.mounting.seat.VxSeat;
import net.xmx.velthoric.physics.body.type.VxBody;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    @Shadow
    public LocalPlayer player;

    @Unique
    private final VxRenderState velthoric_renderState = new VxRenderState();
    @Unique
    private final RVec3 velthoric_tempPos = new RVec3();
    @Unique
    private final Quat velthoric_tempRot = new Quat();

    @Inject(method = "handleKeybinds", at = @At("HEAD"), cancellable = true)
    private void velthoric_handleRideInteraction(CallbackInfo ci) {
        if (Minecraft.getInstance().options.keyUse.consumeClick()) {
            if (velthoric_performSeatRaycast()) {
                ci.cancel();
            }
        }
    }

    /**
     * Performs a raycast to find a rideable seat the player is looking at.
     * This uses a two-phase approach: a fast, coarse AABB check (broad-phase),
     * followed by a precise OBB check (narrow-phase) if the first check succeeds.
     *
     * @return True if a seat was successfully targeted and a ride request was sent.
     */
    @Unique
    private boolean velthoric_performSeatRaycast() {
        if (this.player == null || this.player.isPassenger()) {
            return false;
        }

        float partialTicks = Minecraft.getInstance().getDeltaFrameTime();
        double maxDist = Minecraft.getInstance().gameMode.getPickRange();
        Vec3 cameraPos = this.player.getEyePosition(partialTicks);
        Vec3 viewVec = this.player.getViewVector(partialTicks);
        Vec3 endVec = cameraPos.add(viewVec.x * maxDist, viewVec.y * maxDist, viewVec.z * maxDist);

        VxClientBodyManager bodyManager = VxClientBodyManager.getInstance();
        VxClientMountingManager ridingManager = VxClientMountingManager.INSTANCE;

        VxBody closestBody = null;
        VxSeat closestSeat = null;
        double closestDistSq = Double.MAX_VALUE;

        for (VxBody body : bodyManager.getAllBodies()) {
            if (!body.isInitialized()) continue;

            body.calculateRenderState(partialTicks, velthoric_renderState, velthoric_tempPos, velthoric_tempRot);

            for (VxSeat seat : ridingManager.getSeats(body.getPhysicsId())) {
                // 1. Broad-phase: Fast, coarse check using an axis-aligned bounding box.
                AABB broadPhaseBox = seat.getGlobalAABB(velthoric_renderState.transform);
                if (broadPhaseBox.clip(cameraPos, endVec).isPresent()) {

                    // 2. Narrow-phase: If broad-phase hits, perform a precise check using an oriented bounding box.
                    VxOBB narrowPhaseBox = seat.getGlobalOBB(velthoric_renderState.transform);
                    Optional<Vec3> hitPos = narrowPhaseBox.clip(cameraPos, endVec);

                    if (hitPos.isPresent()) {
                        double distSq = cameraPos.distanceToSqr(hitPos.get());
                        if (distSq < closestDistSq) {
                            closestDistSq = distSq;
                            closestBody = body;
                            closestSeat = seat;
                        }
                    }
                }
            }
        }

        if (closestBody != null) {
            VxPacketHandler.CHANNEL.sendToServer(new C2SRequestMountPacket(closestBody.getPhysicsId(), closestSeat.getId()));
            return true;
        }

        return false;
    }
}