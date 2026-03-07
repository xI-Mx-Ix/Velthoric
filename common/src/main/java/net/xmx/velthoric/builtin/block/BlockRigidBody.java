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
import net.xmx.velthoric.core.network.synchronization.accessor.VxServerAccessor;
import net.xmx.velthoric.core.physics.VxPhysicsLayers;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.core.body.registry.VxBodyType;
import net.xmx.velthoric.core.network.synchronization.VxDataSerializers;
import net.xmx.velthoric.core.network.synchronization.VxSynchronizedData;
import net.xmx.velthoric.core.body.type.VxBody;
import net.xmx.velthoric.core.body.type.factory.VxRigidBodyFactory;
import net.xmx.velthoric.util.VxVoxelShapeUtil;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;

import java.util.UUID;

/**
 * A physics body that represents a single, dynamic block.
 *
 * @author xI-Mx-Ix
 */
public class BlockRigidBody extends VxBody {

    public static final VxServerAccessor<Integer> DATA_BLOCK_STATE_ID = VxServerAccessor.create(BlockRigidBody.class, VxDataSerializers.INTEGER);

    public BlockRigidBody(VxBodyType type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
    }

    @Environment(EnvType.CLIENT)
    public BlockRigidBody(VxBodyType type, UUID id) {
        super(type, id);
    }

    @Override
    protected void defineSyncData(VxSynchronizedData.Builder builder) {
        builder.define(DATA_BLOCK_STATE_ID, Block.getId(Blocks.STONE.defaultBlockState()));
    }

    public void setRepresentedBlockState(BlockState blockState) {
        BlockState state = (blockState != null && !blockState.isAir()) ? blockState : Blocks.STONE.defaultBlockState();
        this.setServerData(DATA_BLOCK_STATE_ID, Block.getId(state));
    }

    public BlockState getRepresentedBlockState() {
        BlockState state = Block.stateById(this.get(DATA_BLOCK_STATE_ID));
        return !state.isAir() ? state : Blocks.STONE.defaultBlockState();
    }

    public static int createJoltBody(VxBody body, VxRigidBodyFactory factory) {
        BlockState stateForShape = Block.stateById(body.get(DATA_BLOCK_STATE_ID));
        if (stateForShape.isAir()) stateForShape = Blocks.STONE.defaultBlockState();

        VoxelShape voxelShape = stateForShape.getCollisionShape(body.getPhysicsWorld().getLevel(), BlockPos.ZERO);

        try (ShapeSettings shapeSettings = VxVoxelShapeUtil.toMutableCompoundShape(voxelShape)) {
            if (shapeSettings == null) {
                VxMainClass.LOGGER.warn("VoxelShape conversion for BlockState {} failed. Using default BoxShape.", stateForShape);
                try (var boxSettings = VxVoxelShapeUtil.toMutableCompoundShape(Blocks.STONE.defaultBlockState().getCollisionShape(body.getPhysicsWorld().getLevel(), BlockPos.ZERO));
                     BodyCreationSettings bcs = new BodyCreationSettings()) {
                    bcs.setMotionType(EMotionType.Dynamic);
                    bcs.setObjectLayer(VxPhysicsLayers.MOVING);
                    return factory.create(boxSettings, bcs);
                }
            }
            try (BodyCreationSettings bcs = new BodyCreationSettings()) {
                bcs.setMotionType(EMotionType.Dynamic);
                bcs.setObjectLayer(VxPhysicsLayers.MOVING);
                return factory.create(shapeSettings, bcs);
            }
        }
    }

    public static void writePersistence(VxBody body, VxByteBuf buf) {
        buf.writeVarInt(body.get(DATA_BLOCK_STATE_ID));
    }

    public static void readPersistence(VxBody body, VxByteBuf buf) {
        int blockStateId = buf.readVarInt();
        BlockState state = Block.stateById(blockStateId);
        if (state.isAir()) {
            body.setServerData(DATA_BLOCK_STATE_ID, Block.getId(Blocks.STONE.defaultBlockState()));
        } else {
            body.setServerData(DATA_BLOCK_STATE_ID, blockStateId);
        }
    }
}