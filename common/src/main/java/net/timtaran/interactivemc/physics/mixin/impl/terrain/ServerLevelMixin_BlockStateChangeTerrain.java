/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.mixin.impl.terrain;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.timtaran.interactivemc.physics.physics.terrain.VxTerrainSystem;
import net.timtaran.interactivemc.physics.physics.world.VxPhysicsWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin class to intercept block state changes in the server level.
 * <p>
 * This mixin is used to update the terrain system when a block state changes.
 * </p>
 *
 * @author xI-Mx-Ix
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin_BlockStateChangeTerrain {

    @Inject(method = "onBlockStateChange", at = @At("HEAD"))
    private void onBlockStateChangeHook(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
        VxTerrainSystem terrainSystem = VxPhysicsWorld.getTerrainSystem(((ServerLevel)(Object)this).dimension());
        if (terrainSystem != null) {
            terrainSystem.onBlockUpdate(pos);
        }
    }
}
