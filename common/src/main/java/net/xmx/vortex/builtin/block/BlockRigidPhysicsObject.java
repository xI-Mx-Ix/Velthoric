package net.xmx.vortex.builtin.block;

import com.github.stephengold.joltjni.BoxShapeSettings;
import com.github.stephengold.joltjni.ShapeSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.xmx.vortex.init.VxMainClass;
import net.xmx.vortex.model.converter.VoxelShapeConverter;
import net.xmx.vortex.physics.object.physicsobject.PhysicsObjectType;
import net.xmx.vortex.physics.object.physicsobject.type.rigid.RigidPhysicsObject;

public class BlockRigidPhysicsObject extends RigidPhysicsObject {

    private BlockState representedBlockState;

    public BlockRigidPhysicsObject(PhysicsObjectType<? extends RigidPhysicsObject> type, Level level) {
        super(type, level);
        this.representedBlockState = Blocks.STONE.defaultBlockState();
    }

    public void setRepresentedBlockState(BlockState blockState) {
        this.representedBlockState = (blockState != null && !blockState.isAir()) ? blockState : Blocks.STONE.defaultBlockState();
    }

    public BlockState getRepresentedBlockState() {
        return (this.representedBlockState != null && !this.representedBlockState.isAir()) ? this.representedBlockState : Blocks.STONE.defaultBlockState();
    }

    protected BlockPos getPositionAsBlockPos() {
        var pos = getCurrentTransform().getTranslation();
        return BlockPos.containing(pos.xx(), pos.yy(), pos.zz());
    }

    @Override
    public ShapeSettings buildShapeSettings() {
        BlockState stateForShape = getRepresentedBlockState();
        VoxelShape voxelShape = stateForShape.getCollisionShape(this.level, this.getPositionAsBlockPos());
        ShapeSettings convertedShapeSettings = VoxelShapeConverter.convert(voxelShape);

        if (convertedShapeSettings != null) {
            return convertedShapeSettings;
        } else {
            VxMainClass.LOGGER.warn("VoxelShape conversion failed for BlockState {}. Using BoxShape as fallback.", stateForShape);
            return new BoxShapeSettings(0.5f, 0.5f, 0.5f);
        }
    }

    @Override
    protected void addAdditionalSpawnData(FriendlyByteBuf buf) {
        buf.writeVarInt(Block.getId(this.representedBlockState));
    }

    @Override
    protected void readAdditionalSpawnData(FriendlyByteBuf buf) {
        int blockStateId = buf.readVarInt();
        this.representedBlockState = Block.stateById(blockStateId);
        if (this.representedBlockState.isAir()) {
            this.representedBlockState = Blocks.STONE.defaultBlockState();
        }
    }
}