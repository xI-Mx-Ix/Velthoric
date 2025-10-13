/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.body;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.thread.ProcessorMailbox;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.entity.ChunkEntities;
import net.minecraft.world.level.entity.EntityPersistentStorage;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/**
 * Mixin to hook into Minecraft's entity storage system.
 * This triggers the loading and saving of custom physics bodies and constraints
 * in sync with the game's chunk entity persistence.
 *
 * @author xI-Mx-Ix
 */
@Mixin(EntityStorage.class)
public abstract class MixinEntityStorage_Persistence implements EntityPersistentStorage<Entity> {

    @Shadow @Final private ServerLevel level;
    @Shadow @Final private ProcessorMailbox<Runnable> entityDeserializerQueue;

    /**
     * Injects logic to save physics bodies and constraints when the chunk's entities are being stored.
     *
     * @param entities The chunk entities being stored.
     * @param ci       Callback info.
     */
    @Inject(method = "storeEntities", at = @At("HEAD"))
    private void onStoreEntities(ChunkEntities<Entity> entities, CallbackInfo ci) {
        VxPhysicsWorld world = VxPhysicsWorld.get(this.level.dimension());
        if (world != null) {
            world.getBodyManager().saveBodiesInChunk(entities.getPos());
            world.getConstraintManager().saveConstraintsInChunk(entities.getPos());
        }
    }

    /**
     * Injects logic to load physics bodies and constraints when the chunk's entities are being loaded.
     * The loading is chained to the original CompletableFuture to ensure correct execution order.
     *
     * @param pos The position of the chunk being loaded.
     * @param cir Callback info for the return value.
     */
    @Inject(method = "loadEntities", at = @At("RETURN"), cancellable = true)
    private void onLoadEntities(ChunkPos pos, CallbackInfoReturnable<CompletableFuture<ChunkEntities<Entity>>> cir) {
        CompletableFuture<ChunkEntities<Entity>> originalFuture = cir.getReturnValue();

        CompletableFuture<ChunkEntities<Entity>> newFuture = originalFuture.thenApplyAsync(chunkEntities -> {
            VxPhysicsWorld world = VxPhysicsWorld.get(this.level.dimension());
            if (world != null) {
                world.getBodyManager().getBodyStorage().loadBodiesInChunk(pos);
                world.getConstraintManager().getConstraintStorage().loadConstraintsInChunk(pos);
            }
            return chunkEntities;
        }, this.entityDeserializerQueue::tell); // Use the 'tell' method as the executor

        cir.setReturnValue(newFuture);
    }
}