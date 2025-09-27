package net.xmx.velthoric.mixin.impl.riding.request;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.network.NetworkHandler;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.object.client.VxRenderState;
import net.xmx.velthoric.physics.object.client.body.VxClientBody;
import net.xmx.velthoric.physics.riding.manager.VxClientRidingManager;
import net.xmx.velthoric.physics.riding.request.C2SRequestRidePacket;
import net.xmx.velthoric.physics.riding.seat.VxSeat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

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

        VxClientObjectManager objectManager = VxClientObjectManager.getInstance();
        VxClientRidingManager ridingManager = VxClientRidingManager.getInstance();

        VxClientBody closestBody = null;
        VxSeat closestSeat = null;
        double closestDistSq = Double.MAX_VALUE;

        for (VxClientBody body : objectManager.getAllObjects()) {
            if (!body.isInitialized()) continue;

            body.calculateRenderState(partialTicks, velthoric_renderState, velthoric_tempPos, velthoric_tempRot);

            for (VxSeat seat : ridingManager.getSeats(body.getId())) {
                AABB worldAABB = seat.getGlobalAABB(velthoric_renderState.transform);

                Optional<Vec3> hitPos = worldAABB.clip(cameraPos, endVec);

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

        if (closestBody != null) {
            NetworkHandler.CHANNEL.sendToServer(new C2SRequestRidePacket(closestBody.getId(), closestSeat.getName()));
            return true;
        }

        return false;
    }
}