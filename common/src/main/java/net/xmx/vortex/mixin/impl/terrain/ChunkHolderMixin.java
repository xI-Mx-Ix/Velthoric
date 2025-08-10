package net.xmx.vortex.mixin.impl.terrain;

import com.mojang.datafixers.util.Either;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.xmx.vortex.physics.terrain.TerrainSystem;
import net.xmx.vortex.physics.world.VxPhysicsWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;

@Mixin(ChunkHolder.class)
public abstract class ChunkHolderMixin {

    @Shadow
    private volatile CompletableFuture<Either<LevelChunk, ChunkHolder.ChunkLoadingFailure>> fullChunkFuture;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onVortexChunkLoad(CallbackInfo ci) {

        this.fullChunkFuture.thenAccept(either -> {

            either.ifLeft(levelChunk -> {

                if (levelChunk != null && levelChunk.getLevel() instanceof ServerLevel serverLevel) {

                    serverLevel.getServer().execute(() -> {

                        TerrainSystem terrainSystem = VxPhysicsWorld.getTerrainSystem(serverLevel.dimension());
                        if (terrainSystem != null) {

                            terrainSystem.onChunkLoadedFromVanilla(levelChunk);
                        }
                    });
                }
            });
        });
    }
}