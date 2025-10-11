/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.object;

import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.xmx.velthoric.physics.object.manager.VxObjectManager;
import net.xmx.velthoric.physics.object.manager.VxObjectNetworkDispatcher;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to hook into {@link ChunkMap} to dispatch physics objects to players
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

    /**
     * Injects into Minecraft's chunk tracking logic. This is called only for
     * chunks whose visibility status is changing for an individual player, making it the most
     * efficient place to trigger client-side object spawning and removal.
     *
     * @param player The player whose view is being updated.
     * @param chunkPos The position of the chunk changing visibility.
     * @param packetCache A mutable object for the chunk packet, not used here.
     * @param wasVisible True if the chunk was visible to the player before this update.
     * @param isVisible True if the chunk is now visible to the player.
     * @param ci Callback info.
     */
    @Inject(method = "updateChunkTracking", at = @At("HEAD"))
    private void velthoric$onUpdateChunkTracking(ServerPlayer player, ChunkPos chunkPos, MutableObject<ClientboundLevelChunkWithLightPacket> packetCache, boolean wasVisible, boolean isVisible, CallbackInfo ci) {
        // This method is only invoked when wasVisible != isVisible.
        if (isVisible == wasVisible) return;

        VxObjectNetworkDispatcher dispatcher = velthoric$getDispatcher();
        if (dispatcher == null) return;

        if (!isVisible) {
            dispatcher.untrackObjectsInChunkForPlayer(player, chunkPos);
        }
    }

    /**
     * Injects after a chunk has been fully loaded and sent to the player. This is the ideal
     * moment to send the spawn packets for any physics objects within that chunk, as the client
     * is now guaranteed to have the chunk context.
     *
     * @param serverPlayer The player who has received the chunk.
     * @param mutableObject The chunk packet data, not used here.
     * @param levelChunk The chunk that was loaded.
     * @param ci Callback info.
     */
    @Inject(method = "playerLoadedChunk", at = @At("TAIL"))
    private void velthoric$onPlayerLoadedChunk(ServerPlayer serverPlayer, MutableObject<ClientboundLevelChunkWithLightPacket> mutableObject, LevelChunk levelChunk, CallbackInfo ci) {
        VxObjectNetworkDispatcher dispatcher = velthoric$getDispatcher();
        if (dispatcher != null) {
            // Player is now tracking this chunk, so send all physics objects within it.
            dispatcher.trackObjectsInChunkForPlayer(serverPlayer, levelChunk.getPos());
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
            VxObjectNetworkDispatcher dispatcher = velthoric$getDispatcher();
            if (dispatcher != null) {
                dispatcher.onPlayerDisconnect(player);
            }
        }
    }
}