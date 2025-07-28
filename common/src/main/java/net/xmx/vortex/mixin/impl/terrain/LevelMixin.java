package net.xmx.vortex.mixin.impl.terrain;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.xmx.vortex.physics.terrain.TerrainSystem;
import net.xmx.vortex.physics.world.VxPhysicsWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelMixin {

    @Shadow public abstract boolean isClientSide();
    @Shadow public abstract ResourceKey<Level> dimension();

    @Inject(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z",
            at = @At(value = "RETURN", ordinal = 0)
    )
    private void onSetBlock(BlockPos pos, BlockState newState, int flags, CallbackInfoReturnable<Boolean> cir) {
        if (!isClientSide() && cir.getReturnValue()) {
            TerrainSystem terrainSystem = VxPhysicsWorld.getTerrainSystem(this.dimension());
            if (terrainSystem != null) {
                BlockState oldState = this.getBlockState(pos);
                terrainSystem.onBlockUpdate(pos, oldState, newState);
            }
        }
    }

    @Inject(
            method = "destroyBlock",
            at = @At(value = "HEAD")
    )
    private void onDestroyBlock(BlockPos pos, boolean dropBlock, Entity entity, int recursionLeft, CallbackInfoReturnable<Boolean> cir) {
        if (!isClientSide()) {
            TerrainSystem terrainSystem = VxPhysicsWorld.getTerrainSystem(this.dimension());
            if (terrainSystem != null) {
                BlockState oldState = this.getBlockState(pos);
                terrainSystem.onBlockUpdate(pos, oldState, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }
        }
    }

    @Shadow
    public abstract BlockState getBlockState(BlockPos pos);
}