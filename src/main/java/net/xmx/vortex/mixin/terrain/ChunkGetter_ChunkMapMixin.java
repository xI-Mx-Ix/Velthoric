package net.xmx.vortex.mixin.terrain;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.xmx.vortex.physics.terrain.TerrainSystem;
import net.xmx.vortex.physics.world.VxPhysicsWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(ChunkMap.class)
public abstract class ChunkGetter_ChunkMapMixin {

    @Shadow @Final
    private ServerLevel level;

    @Inject(method = "tick", at = @At("HEAD"))
    private void vortex_processTerrainSystemMainThreadTasks(BooleanSupplier pHasMoreTime, CallbackInfo ci) {

        VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(this.level.dimension());

        if (physicsWorld != null) {
            TerrainSystem terrainSystem = physicsWorld.getTerrainSystem();
            if (terrainSystem != null) {

                terrainSystem.processPendingSnapshotsOnMainThread();
            }
        }
    }
}