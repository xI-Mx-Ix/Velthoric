/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.mixin.impl.body;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.timtaran.interactivemc.physics.physics.body.manager.VxBodyManager;
import net.timtaran.interactivemc.physics.physics.body.manager.VxNetworkDispatcher;
import net.timtaran.interactivemc.physics.physics.world.VxPhysicsWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to hook into {@link ChunkMap} to dispatch physics bodies to players
 * in a way that is synchronized with vanilla chunk and entity tracking. This class
 * handles the dynamic sending and removal of objects as players move around the world.
 *
 * @author xI-Mx-Ix
 */
@Mixin(ChunkMap.class)
public abstract class MixinChunkMap_ChunkTracking {

    @Shadow @Final ServerLevel level;

    /**
     * Retrieves the network dispatcher for the current physics world.
     *
     * @return The dispatcher instance, or null if not available.
     */
    @Unique
    private VxNetworkDispatcher velthoric$getDispatcher() {
        VxPhysicsWorld world = VxPhysicsWorld.get(this.level.dimension());
        if (world != null) {
            VxBodyManager manager = world.getBodyManager();
            if (manager != null) {
                return manager.getNetworkDispatcher();
            }
        }
        return null;
    }

    /**
     * Injects into the chunk marking logic to track when chunks are being sent to players.
     * This is called when a chunk is marked to be sent to a player, making it the ideal
     * moment to send the spawn packets for any physics bodies within that chunk.
     *
     * @param player The player who will receive the chunk.
     * @param chunkPos The position of the chunk being marked.
     * @param ci Callback info.
     */
    @Inject(method = "markChunkPendingToSend", at = @At("TAIL"))
    private void velthoric$onMarkChunkPendingToSend(ServerPlayer player, ChunkPos chunkPos, CallbackInfo ci) {
        VxNetworkDispatcher dispatcher = velthoric$getDispatcher();
        if (dispatcher != null) {
            // Player is now tracking this chunk, so send all physics bodies within it.
            dispatcher.trackBodiesInChunkForPlayer(player, chunkPos);
        }
    }

    /**
     * Injects into the chunk drop logic to track when chunks are being removed from a player's view.
     * This ensures physics bodies are properly untracked when the chunk is no longer visible.
     *
     * @param player The player whose view is being updated.
     * @param chunkPos The position of the chunk being dropped.
     */
    @Inject(method = "dropChunk", at = @At("HEAD"))
    private static void velthoric$onDropChunk(ServerPlayer player, ChunkPos chunkPos, CallbackInfo ci) {
        if (player.level() instanceof ServerLevel serverLevel) {
            VxPhysicsWorld world = VxPhysicsWorld.get(serverLevel.dimension());
            if (world != null) {
                VxBodyManager manager = world.getBodyManager();
                if (manager != null) {
                    VxNetworkDispatcher dispatcher = manager.getNetworkDispatcher();
                    if (dispatcher != null) {
                        dispatcher.untrackBodiesInChunkForPlayer(player, chunkPos);
                    }
                }
            }
        }
    }

    /**
     * Injects into the player status update logic to detect when a player disconnects.
     * This ensures a clean-up of all tracking data associated with the player.
     *
     * @param player The player whose status is being updated.
     * @param track False indicates the player is being untracked (e.g., disconnecting).
     * @param ci Callback info.
     */
    @Inject(method = "updatePlayerStatus", at = @At("TAIL"))
    private void velthoric$onPlayerLeave(ServerPlayer player, boolean track, CallbackInfo ci) {
        if (!track) {
            VxNetworkDispatcher dispatcher = velthoric$getDispatcher();
            if (dispatcher != null) {
                dispatcher.onPlayerDisconnect(player);
            }
        }
    }
}