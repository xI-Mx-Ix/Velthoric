/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.persistence;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to hook into the core ChunkMap lifecycle events.
 * This guarantees that physics data follows the exact same lifecycle as vanilla chunks
 * and ensures data is saved even during auto-saves when chunks might not be marked 'dirty' by Vanilla.
 *
 * @author xI-Mx-Ix
 */
@Mixin(ChunkMap.class)
public class MixinChunkMap_Persistence {

    @Shadow @Final ServerLevel level;

    /**
     * Shadow the map containing all currently visible/loaded chunks.
     * We need this to iterate over chunks during auto-save.
     */
    @Shadow private volatile Long2ObjectLinkedOpenHashMap<ChunkHolder> visibleChunkMap;

    /**
     * Injects into the global save method (Auto-Save, Save-All, Shutdown).
     * <p>
     * <b>Smart Saving Logic:</b>
     * Minecraft normally skips saving chunks if they are not marked as "unsaved" (dirty).
     * Since physics movements do not set this Vanilla flag, we must check manually.
     * <p>
     * To avoid double-saving:
     * <ul>
     *     <li>If {@code chunk.isUnsaved()} is <b>true</b>: We do NOTHING here. Minecraft will proceed
     *     to call {@link #onSaveChunk} momentarily, handling the save there.</li>
     *     <li>If {@code chunk.isUnsaved()} is <b>false</b>: Minecraft would skip this chunk.
     *     We must manually force a physics save to capture body movements.</li>
     * </ul>
     */
    @Inject(method = "saveAllChunks", at = @At("HEAD"))
    private void onSaveAllChunks(boolean flush, CallbackInfo ci) {
        VxPhysicsWorld world = VxPhysicsWorld.get(level.dimension());

        if (world != null) {
            for (ChunkHolder holder : this.visibleChunkMap.values()) {
                ChunkAccess chunk = holder.getLatestChunk();

                if (chunk != null && !chunk.isUnsaved()) {
                    world.saveChunkData(holder.getPos());
                }
            }

            world.flushAllPersistence(flush);
        }
    }

    /**
     * Injects into the specific chunk save method.
     */
    @Inject(method = "save", at = @At("HEAD"))
    private void onSaveChunk(ChunkAccess chunk, CallbackInfoReturnable<Boolean> cir) {
        VxPhysicsWorld world = VxPhysicsWorld.get(level.dimension());
        if (world != null) {
            world.saveChunkData(chunk.getPos());
        }
    }

    /**
     * Injects into the chunk unload scheduling.
     */
    @Inject(method = "scheduleUnload", at = @At("HEAD"))
    private void onUnloadChunk(long chunkPos, ChunkHolder chunkHolder, CallbackInfo ci) {
        VxPhysicsWorld world = VxPhysicsWorld.get(level.dimension());
        ChunkPos pos = chunkHolder.getPos();

        if (world != null) {
            world.saveChunkData(pos);
            world.unloadChunkData(pos);
        }
    }
}