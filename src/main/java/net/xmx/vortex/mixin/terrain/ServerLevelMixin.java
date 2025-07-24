package net.xmx.vortex.mixin.terrain;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.xmx.vortex.physics.terrain.TerrainSystem;
import net.xmx.vortex.physics.world.VxPhysicsWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.function.BooleanSupplier;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    @Inject(method = "onBlockStateChange", at = @At("HEAD"))
    private void onBlockStateChangeHook(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
        TerrainSystem terrainSystem = VxPhysicsWorld.getTerrainSystem(((ServerLevel)(Object)this).dimension());
        if (terrainSystem != null) {
            terrainSystem.onBlockUpdate(pos, oldState, newState);
        }
    }
}