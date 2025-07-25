package net.xmx.vortex.mixin.terrain;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.xmx.vortex.physics.terrain.ChunkProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(ChunkMap.class)
public abstract class ChunkGetter_ChunkMapMixin {

    @Shadow @Final ServerLevel level;

    @Inject(method = "tick", at = @At("HEAD"))
    private void vortex_processTerrainSnapshots(BooleanSupplier pHasMoreTime, CallbackInfo ci) {
        ChunkProvider.processSnapshotsForLevel(this.level);
    }
}