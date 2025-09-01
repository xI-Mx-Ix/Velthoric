package net.xmx.velthoric.ship;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.xmx.velthoric.builtin.VxRegisteredObjects;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import net.xmx.velthoric.ship.plot.VxPlot;
import net.xmx.velthoric.ship.plot.VxPlotManager;

import java.util.Optional;
import java.util.UUID;

public class VxShipAssembler {

    public static boolean assemble(ServerLevel level, BoundingBox sourceBox) {
        VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(level.dimension());
        if (physicsWorld == null) {
            return false;
        }

        VxPlotManager plotManager = physicsWorld.getPlotManager();

        VxTransform transform = new VxTransform();
        transform.getTranslation().set(
                sourceBox.getCenter().getX() + 0.5,
                sourceBox.getCenter().getY() + 0.5,
                sourceBox.getCenter().getZ() + 0.5
        );

        Optional<VxStructureBody> bodyOptional = physicsWorld.getObjectManager().createRigidBody(
                VxRegisteredObjects.STRUCTURE_BODY,
                transform,
                (VxStructureBody b) -> {}
        );

        if (bodyOptional.isEmpty()) {
            return false;
        }

        VxStructureBody structureBody = bodyOptional.get();
        UUID shipId = structureBody.getPhysicsId();

        VxPlot targetPlot = plotManager.assignShipToAvailablePlot(shipId);
        structureBody.setPlot(targetPlot);
        BoundingBox targetBox = targetPlot.getBounds();

        ChunkPos minChunk = new ChunkPos(targetBox.minX() >> 4, targetBox.minZ() >> 4);
        ChunkPos maxChunk = new ChunkPos(targetBox.maxX() >> 4, targetBox.maxZ() >> 4);
        for (int x = minChunk.x; x <= maxChunk.x; x++) {
            for (int z = minChunk.z; z <= maxChunk.z; z++) {
                level.getChunk(x, z, ChunkStatus.FULL, true);
            }
        }

        BlockPos sourceOrigin = new BlockPos(sourceBox.minX(), sourceBox.minY(), sourceBox.minZ());
        BlockPos targetOrigin = new BlockPos(targetBox.minX(), targetBox.minY(), targetBox.minZ());

        BlockPos.MutableBlockPos targetPos = new BlockPos.MutableBlockPos();
        for (BlockPos sourcePos : BlockPos.betweenClosed(sourceBox.minX(), sourceBox.minY(), sourceBox.minZ(), sourceBox.maxX(), sourceBox.maxY(), sourceBox.maxZ())) {
            BlockPos localPos = sourcePos.subtract(sourceOrigin);
            targetPos.set(targetOrigin.getX() + localPos.getX(), targetOrigin.getY() + localPos.getY(), targetOrigin.getZ() + localPos.getZ());

            BlockState state = level.getBlockState(sourcePos);
            level.setBlock(targetPos, state, 3);

            BlockEntity be = level.getBlockEntity(sourcePos);
            if (be != null) {
                CompoundTag nbt = be.saveWithFullMetadata();
                level.removeBlockEntity(sourcePos);
                BlockEntity newBe = level.getBlockEntity(targetPos);
                if (newBe != null) {
                    newBe.load(nbt);
                }
            }
            level.setBlock(sourcePos, Blocks.AIR.defaultBlockState(), 3);
        }

        return true;
    }
}