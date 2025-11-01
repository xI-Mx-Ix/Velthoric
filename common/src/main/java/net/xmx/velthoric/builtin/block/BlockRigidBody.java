/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.builtin.block;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.ShapeSettings;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.world.VxLayers;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.body.registry.VxBodyType;
import net.xmx.velthoric.physics.body.sync.VxDataAccessor;
import net.xmx.velthoric.physics.body.sync.VxDataSerializers;
import net.xmx.velthoric.physics.body.sync.VxSynchronizedData;
import net.xmx.velthoric.physics.body.type.VxRigidBody;
import net.xmx.velthoric.physics.body.type.factory.VxRigidBodyFactory;
import net.xmx.velthoric.physics.body.util.VxVoxelShapeUtil;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.UUID;

/**
 * A physics body that represents a single, dynamic block.
 *
 * @author xI-Mx-Ix
 */
public class BlockRigidBody extends VxRigidBody {

    public static final VxDataAccessor<Integer> DATA_BLOCK_STATE_ID = VxDataAccessor.create(BlockRigidBody.class, VxDataSerializers.INTEGER);

    /**
     * Server-side constructor.
     */
    public BlockRigidBody(VxBodyType<BlockRigidBody> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
    }

    /**
     * Client-side constructor.
     */
    @Environment(EnvType.CLIENT)
    public BlockRigidBody(VxBodyType<BlockRigidBody> type, UUID id) {
        super(type, id);
    }

    @Override
    protected void defineSyncData(VxSynchronizedData.Builder builder) {
        builder.define(DATA_BLOCK_STATE_ID, Block.getId(Blocks.STONE.defaultBlockState()));
    }

    public void setRepresentedBlockState(BlockState blockState) {
        BlockState state = (blockState != null && !blockState.isAir()) ? blockState : Blocks.STONE.defaultBlockState();
        this.setSyncData(DATA_BLOCK_STATE_ID, Block.getId(state));
    }

    public BlockState getRepresentedBlockState() {
        BlockState state = Block.stateById(this.getSyncData(DATA_BLOCK_STATE_ID));
        return !state.isAir() ? state : Blocks.STONE.defaultBlockState();
    }

    @Override
    public int createJoltBody(VxRigidBodyFactory factory) {
        BlockState stateForShape = getRepresentedBlockState();
        VoxelShape voxelShape = stateForShape.getCollisionShape(this.physicsWorld.getLevel(), BlockPos.ZERO);

        try (ShapeSettings shapeSettings = VxVoxelShapeUtil.toMutableCompoundShape(voxelShape)) {
            if (shapeSettings == null) {
                VxMainClass.LOGGER.warn("VoxelShape conversion for BlockState {} failed. Using default BoxShape.", stateForShape);
                try (var boxSettings = VxVoxelShapeUtil.toMutableCompoundShape(Blocks.STONE.defaultBlockState().getCollisionShape(this.physicsWorld.getLevel(), BlockPos.ZERO));
                     BodyCreationSettings bcs = new BodyCreationSettings()) {
                    bcs.setMotionType(EMotionType.Dynamic);
                    bcs.setObjectLayer(VxLayers.DYNAMIC);
                    return factory.create(boxSettings, bcs);
                }
            }
            try (BodyCreationSettings bcs = new BodyCreationSettings()) {
                bcs.setMotionType(EMotionType.Dynamic);
                bcs.setObjectLayer(VxLayers.DYNAMIC);
                return factory.create(shapeSettings, bcs);
            }
        }
    }

    @Override
    public void writePersistenceData(VxByteBuf buf) {
        buf.writeVarInt(this.getSyncData(DATA_BLOCK_STATE_ID));
    }

    @Override
    public void readPersistenceData(VxByteBuf buf) {
        int blockStateId = buf.readVarInt();
        BlockState state = Block.stateById(blockStateId);
        if (state.isAir()) {
            this.setSyncData(DATA_BLOCK_STATE_ID, Block.getId(Blocks.STONE.defaultBlockState()));
        } else {
            this.setSyncData(DATA_BLOCK_STATE_ID, blockStateId);
        }
    }
}