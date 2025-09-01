package net.xmx.velthoric.mixin.impl.ship;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import net.xmx.velthoric.ship.VxStructureBody;
import net.xmx.velthoric.ship.plot.VxPlot;
import net.xmx.velthoric.ship.plot.VxPlotManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    @Inject(
            method = "onBlockStateChange",
            at = @At("HEAD")
    )
    private void onSetBlock(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
        ServerLevel self = (ServerLevel)(Object)this;
        VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(self.dimension());
        if (physicsWorld != null) {
            VxPlotManager plotManager = physicsWorld.getPlotManager();
            ChunkPos chunkPos = new ChunkPos(pos);

            Integer plotId = plotManager.getPlotIdForChunk(chunkPos);

            if (plotId != null) {
                VxPlot plot = plotManager.getPlotById(plotId);
                if (plot != null) {
                    plot.getAssignedShipId().ifPresent(shipId -> {
                        physicsWorld.getObjectManager().getObject(shipId).ifPresent(obj -> {
                            if (obj instanceof VxStructureBody structureBody) {
                                structureBody.markShapeDirty();
                            }
                        });
                    });
                }
            }
        }
    }
}