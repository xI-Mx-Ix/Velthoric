/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.persistence;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.entity.ChunkEntities;
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
 * Mixin to hook directly into the Minecraft EntityStorage system.
 * This binds the lifecycle of physics data (Bodies & Constraints) 1:1 with vanilla Entities.
 * <p>
 * By hooking here, we ensure that whenever Minecraft decides to Load, Save, or Flush
 * entity data, the corresponding physics data is handled simultaneously.
 *
 * @author xI-Mx-Ix
 */
@Mixin(EntityStorage.class)
public class MixinEntityStorage_Persistence {

    @Shadow @Final private ServerLevel level;

    /**
     * Injects into the start of the entity loading process.
     * Triggers the asynchronous loading of physics bodies and constraints for the specified chunk.
     */
    @Inject(method = "loadEntities", at = @At("HEAD"))
    private void onLoadEntities(ChunkPos pos, CallbackInfoReturnable<CompletableFuture<ChunkEntities<Entity>>> cir) {
        VxPhysicsWorld world = VxPhysicsWorld.get(this.level.dimension());
        if (world != null) {
            world.getBodyManager().getBodyStorage().loadChunk(pos).thenAccept(dataList -> {
                // Schedule instantiation on main thread/physics thread
                world.execute(() -> {
                    for (var data : dataList) world.getBodyManager().addSerializedBody(data);
                });
            });

            world.getConstraintManager().getConstraintStorage().loadChunk(pos).thenAccept(constraints -> {
                world.execute(() -> {
                    for (var c : constraints) world.getConstraintManager().addConstraintFromStorage(c);
                });
            });
        }
    }

    /**
     * Injects into the entity saving process.
     * This is called during auto-save and chunk unload.
     * We immediately serialize the physics state on the main thread and queue the I/O task.
     */
    @Inject(method = "storeEntities", at = @At("HEAD"))
    private void onStoreEntities(ChunkEntities<Entity> entities, CallbackInfo ci) {
        VxPhysicsWorld world = VxPhysicsWorld.get(this.level.dimension());
        if (world != null) {
            ChunkPos pos = entities.getPos();
            
            // Serialize and save physics data matching this chunk
            world.getBodyManager().saveBodiesInChunk(pos);
            world.getConstraintManager().saveConstraintsInChunk(pos);
        }
    }
}