/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.builtin.block;

import com.github.stephengold.joltjni.enumerate.EBodyType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.object.client.body.VxClientRigidBody;
import net.xmx.velthoric.physics.object.sync.VxDataAccessor;
import net.xmx.velthoric.physics.object.sync.VxDataSerializers;

import java.util.UUID;

/**
 * Client-side handle for a dynamic block physics object.
 *
 * @author xI-Mx-Ix
 */
public class BlockClientRigidBody extends VxClientRigidBody {

    public static final VxDataAccessor<Integer> DATA_BLOCK_STATE_ID = VxDataAccessor.create(BlockClientRigidBody.class, VxDataSerializers.INTEGER);

    public BlockClientRigidBody(UUID id, ResourceLocation typeId, VxClientObjectManager manager, int dataStoreIndex, EBodyType objectType) {
        super(id, typeId, manager, dataStoreIndex, objectType);
    }

    @Override
    protected void defineSyncData() {
        this.synchronizedData.define(DATA_BLOCK_STATE_ID, Block.getId(Blocks.STONE.defaultBlockState()));
    }
}