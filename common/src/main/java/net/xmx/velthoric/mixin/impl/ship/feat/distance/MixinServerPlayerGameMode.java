package net.xmx.velthoric.mixin.impl.ship.feat.distance;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.ship.VxShipUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerPlayerGameMode.class)
public abstract class MixinServerPlayerGameMode {

    @Final
    @Shadow
    protected ServerPlayer player;

    @Redirect(
            method = "handleBlockBreakAction",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"
            )
    )
    private double velthoric$correctBlockBreakReachCheck(Vec3 playerEyePos, Vec3 blockCenter) {
        return VxShipUtil.sqrdShips(this.player, blockCenter.x(), blockCenter.y(), blockCenter.z());
    }
}