/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.object;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to hook into the chunk unloading process.
 * This triggers the in-memory cleanup of physics objects and constraints
 * when a chunk is scheduled to be unloaded from the server.
 *
 * @author xI-Mx-Ix
 */
@Mixin(ChunkMap.class)
public class MixinChunkMap_UnloadObjects {

    @Shadow @Final ServerLevel level;

    /**
     * Injects logic to handle the unloading of physics objects and constraints
     * from the simulation when a chunk is scheduled for unloading.
     *
     * @param chunkPos    The long-encoded position of the chunk being unloaded.
     * @param chunkHolder The ChunkHolder for the chunk.
     * @param ci          Callback info.
     */
    @Inject(method = "scheduleUnload", at = @At("HEAD"))
    private void onScheduleUnload(long chunkPos, ChunkHolder chunkHolder, CallbackInfo ci) {
        ChunkPos pos = new ChunkPos(chunkPos);
        VxPhysicsWorld world = VxPhysicsWorld.get(this.level.dimension());
        if (world != null) {
            world.getObjectManager().onChunkUnload(pos);
            world.getConstraintManager().onChunkUnload(pos);
        }
    }
}