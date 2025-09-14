/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.ship.chunk;

import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.xmx.velthoric.ship.VxShipUtil;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public abstract class MixinPlayerList {

    @Shadow @Final private MinecraftServer server;

    @Shadow
    public abstract void broadcast(@Nullable Player except, double x, double y, double z, double radius, ResourceKey<Level> dimension, Packet<?> packet);

    @Inject(
            method = "broadcast",
            at = @At("HEAD"),
            cancellable = true
    )
    private void velthoric$transformBroadcastCoordinates(
            @Nullable Player except, double x, double y, double z, double radius,
            ResourceKey<Level> dimension, Packet<?> packet, CallbackInfo ci) {

        Level level = this.server.getLevel(dimension);
        if (level == null) {
            return; 
        }

        net.minecraft.world.phys.Vec3 worldPos = VxShipUtil.getTruePosition(level, x, y, z);

        if (worldPos.x != x || worldPos.y != y || worldPos.z != z) {
            this.broadcast(except, worldPos.x, worldPos.y, worldPos.z, radius, dimension, packet);
            ci.cancel();
        }

    }
}