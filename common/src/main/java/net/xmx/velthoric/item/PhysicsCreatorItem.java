/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.enumerate.EActivation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.xmx.velthoric.builtin.VxRegisteredBodies;
import net.xmx.velthoric.builtin.block.BlockRigidBody;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.physics.body.manager.VxBodyManager;
import net.xmx.velthoric.physics.body.util.VxVoxelShapeUtil;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

/**
 * @author xI-Mx-Ix
 */
public class PhysicsCreatorItem extends Item {

    public PhysicsCreatorItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();

        if (level.isClientSide()) {
            return super.useOn(context);
        }

        BlockPos pos = context.getClickedPos();
        BlockState originalState = level.getBlockState(pos);

        if (originalState.isAir() || originalState.getDestroySpeed(level, pos) < 0) {
            return InteractionResult.FAIL;
        }

        VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(level.dimension());
        if (physicsWorld == null) {
            VxMainClass.LOGGER.error("Could not find VxPhysicsWorld for the level!");
            return InteractionResult.FAIL;
        }

        VxBodyManager bodyManager = physicsWorld.getBodyManager();

        try (var ignored = VxVoxelShapeUtil.toMutableCompoundShape(
                originalState.getVisualShape(level, pos, CollisionContext.empty()))) {
            if (ignored == null) {
                VxMainClass.LOGGER.warn("Could not generate a valid physics shape for the block: {}", originalState);
                return InteractionResult.FAIL;
            }
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Error during pre-check of physics shape creation", e);
            return InteractionResult.FAIL;
        }

        try {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_IMMEDIATE);

            RVec3 position = new RVec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            Quat rotation = Quat.sIdentity();
            VxTransform transform = new VxTransform(position, rotation);

            BlockRigidBody body = bodyManager.createRigidBody(
                    VxRegisteredBodies.BLOCK,
                    transform,
                    EActivation.Activate,
                    b -> b.setRepresentedBlockState(originalState)
            );

            if (body == null) {
                throw new IllegalStateException("Body creation returned null.");
            }

            return super.useOn(context);

        } catch (Exception e) {
            VxMainClass.LOGGER.error("Error creating physics body, restoring original block.", e);
            level.setBlock(pos, originalState, Block.UPDATE_ALL | Block.UPDATE_IMMEDIATE);
            return InteractionResult.FAIL;
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
