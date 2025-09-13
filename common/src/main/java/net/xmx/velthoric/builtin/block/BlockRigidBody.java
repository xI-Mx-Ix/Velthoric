/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.builtin.block;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.BoxShapeSettings;
import com.github.stephengold.joltjni.ShapeRefC;
import com.github.stephengold.joltjni.ShapeSettings;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.object.VxObjectType;
import net.xmx.velthoric.physics.object.type.VxRigidBody;
import net.xmx.velthoric.physics.object.util.VxVoxelShapeUtil;
import net.xmx.velthoric.physics.world.VxLayers;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.UUID;

public class BlockRigidBody extends VxRigidBody {

    private BlockState representedBlockState;

    public BlockRigidBody(VxObjectType<BlockRigidBody> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
        this.representedBlockState = Blocks.STONE.defaultBlockState();
    }

    public void setRepresentedBlockState(BlockState blockState) {
        this.representedBlockState = (blockState != null && !blockState.isAir()) ? blockState : Blocks.STONE.defaultBlockState();
        this.markDataDirty();
    }

    public BlockState getRepresentedBlockState() {
        return (this.representedBlockState != null && !this.representedBlockState.isAir()) ? this.representedBlockState : Blocks.STONE.defaultBlockState();
    }

    @Override
    public ShapeSettings createShapeSettings() {
        BlockState stateForShape = getRepresentedBlockState();
        VoxelShape voxelShape = stateForShape.getCollisionShape(this.world.getLevel(), BlockPos.ZERO);

        ShapeSettings convertedShapeSettings = VxVoxelShapeUtil.toMutableCompoundShape(voxelShape);

        if (convertedShapeSettings != null) {
            return convertedShapeSettings;
        } else {
            VxMainClass.LOGGER.warn("VoxelShape conversion for BlockState {} failed. Using BoxShape as fallback.", stateForShape);
            return new BoxShapeSettings(0.5f, 0.5f, 0.5f);
        }
    }

    @Override
    public BodyCreationSettings createBodyCreationSettings(ShapeRefC shapeRef) {
        var bcs = new BodyCreationSettings(
                shapeRef,
                this.getGameTransform().getTranslation(),
                this.getGameTransform().getRotation(),
                EMotionType.Dynamic,
                VxLayers.DYNAMIC);
        return bcs;
    }

    @Override
    public void writeCreationData(VxByteBuf buf) {
        buf.writeVarInt(Block.getId(this.representedBlockState));
    }

    @Override
    public void readCreationData(VxByteBuf buf) {
        int blockStateId = buf.readVarInt();
        this.representedBlockState = Block.stateById(blockStateId);
        if (this.representedBlockState.isAir()) {
            this.representedBlockState = Blocks.STONE.defaultBlockState();
        }
    }
}
