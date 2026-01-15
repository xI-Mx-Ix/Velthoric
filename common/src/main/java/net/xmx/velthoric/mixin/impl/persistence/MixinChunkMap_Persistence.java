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
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
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
            // Iterate over all currently loaded chunks to catch those with only physics changes
            for (ChunkHolder holder : this.visibleChunkMap.values()) {
                ChunkAccess chunk = holder.getLatestChunk();

                // Only manually save if Vanilla WON'T save it (i.e. isUnsaved is false).
                // If isUnsaved is true, our hook in onSaveChunk will handle it efficiently.
                if (chunk != null && !chunk.isUnsaved()) {
                    ChunkPos pos = holder.getPos();
                    world.getBodyManager().saveBodiesInChunk(pos);
                    world.getConstraintManager().saveConstraintsInChunk(pos);
                }
            }

            // If this is a blocking save (e.g. Save & Quit), we flush our persistence immediately.
            // For auto-save (flush=false), the data is just queued to the I/O thread.
            if (flush) {
                world.getBodyManager().flushPersistence(true);
                world.getConstraintManager().flushPersistence(true);
            } else {
                // For auto-save, trigger non-blocking flush to keep queue size low
                world.getBodyManager().flushPersistence(false);
                world.getConstraintManager().flushPersistence(false);
            }
        }
    }

    /**
     * Injects into the specific chunk save method.
     * This handles:
     * 1. Standard dirty chunks during auto-save (skipped by logic in onSaveAllChunks above).
     * 2. Chunks saved explicitly by other game mechanics.
     */
    @Inject(method = "save", at = @At("HEAD"))
    private void onSaveChunk(ChunkAccess chunk, CallbackInfoReturnable<Boolean> cir) {
        VxPhysicsWorld world = VxPhysicsWorld.get(level.dimension());

        if (world != null) {
            // Serialize and queue storage for bodies/constraints in this chunk.
            world.getBodyManager().saveBodiesInChunk(chunk.getPos());
            world.getConstraintManager().saveConstraintsInChunk(chunk.getPos());
        }
    }

    /**
     * Injects into the chunk unload scheduling.
     * This is called when a player leaves a chunk or the server frees memory.
     */
    @Inject(method = "scheduleUnload", at = @At("HEAD"))
    private void onUnloadChunk(long chunkPos, ChunkHolder chunkHolder, CallbackInfo ci) {
        VxPhysicsWorld world = VxPhysicsWorld.get(level.dimension());
        ChunkPos pos = chunkHolder.getPos();

        if (world != null) {
            // 1. Safety Save: Ensure data is serialized before removal.
            world.getBodyManager().saveBodiesInChunk(pos);
            world.getConstraintManager().saveConstraintsInChunk(pos);

            // 2. Memory Cleanup: Remove bodies from the active simulation.
            world.getBodyManager().onChunkUnload(pos);
            world.getConstraintManager().onChunkUnload(pos);
        }
    }
}