/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.persistence;

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
 * This guarantees that physics data follows the exact same lifecycle as vanilla chunks.
 *
 * @author xI-Mx-Ix
 */
@Mixin(ChunkMap.class)
public class MixinChunkMap_Persistence {

    @Shadow @Final ServerLevel level;

    /**
     * Injects into the specific chunk save method.
     * This is called by auto-save, save-all, and during shutdown.
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