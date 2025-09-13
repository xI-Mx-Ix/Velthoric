/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.object;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.velthoric.physics.object.manager.VxObjectManager;
import net.xmx.velthoric.physics.object.manager.VxObjectNetworkDispatcher;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin_DispatchObjectsToPlayers {

    @Shadow @Final
    ServerLevel level;

    @Unique
    private VxObjectNetworkDispatcher velthoric$getDispatcher() {
        VxPhysicsWorld world = VxPhysicsWorld.get(this.level.dimension());
        if (world != null) {
            VxObjectManager manager = world.getObjectManager();
            if (manager != null) {
                return manager.getNetworkDispatcher();
            }
        }
        return null;
    }

    @Inject(method = "move", at = @At("TAIL"))
    private void velthoric$onPlayerMove(ServerPlayer player, CallbackInfo ci) {
        VxObjectNetworkDispatcher dispatcher = velthoric$getDispatcher();
        if (dispatcher != null) {
            dispatcher.updatePlayerTracking(player);
        }
    }

    @Inject(method = "updatePlayerStatus", at = @At("TAIL"))
    private void velthoric$onPlayerStatusUpdate(ServerPlayer player, boolean track, CallbackInfo ci) {
        VxObjectNetworkDispatcher dispatcher = velthoric$getDispatcher();
        if (dispatcher != null) {
            if (track) {
                dispatcher.onPlayerJoin(player);
            } else {
                dispatcher.onPlayerDisconnect(player);
            }
        }
    }
}
