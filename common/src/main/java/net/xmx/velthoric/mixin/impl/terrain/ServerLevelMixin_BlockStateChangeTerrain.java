/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.terrain;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.xmx.velthoric.physics.terrain.VxTerrainSystem;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin_BlockStateChangeTerrain {

    @Inject(method = "onBlockStateChange", at = @At("HEAD"))
    private void onBlockStateChangeHook(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
        VxTerrainSystem terrainSystem = VxPhysicsWorld.getTerrainSystem(((ServerLevel)(Object)this).dimension());
        if (terrainSystem != null) {
            terrainSystem.onBlockUpdate(pos, oldState, newState);
        }
    }
}
