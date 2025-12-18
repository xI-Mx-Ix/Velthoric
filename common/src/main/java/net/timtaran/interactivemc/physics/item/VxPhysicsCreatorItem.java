/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.item;

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
import net.timtaran.interactivemc.physics.builtin.VxRegisteredBodies;
import net.timtaran.interactivemc.physics.builtin.block.BlockRigidBody;
import net.timtaran.interactivemc.physics.init.VxMainClass;
import net.timtaran.interactivemc.physics.math.VxTransform;
import net.timtaran.interactivemc.physics.physics.body.manager.VxBodyManager;
import net.timtaran.interactivemc.physics.physics.body.util.VxVoxelShapeUtil;
import net.timtaran.interactivemc.physics.physics.world.VxPhysicsWorld;

/**
 * An item that converts a standard block into a physics-based rigid body upon use.
 * @author xI-Mx-Ix
 */
public class VxPhysicsCreatorItem extends Item {

    public VxPhysicsCreatorItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    /**
     * Called when this item is used on a block.
     * It removes the block from the world and replaces it with a dynamic rigid body
     * that represents the original block's state and shape.
     *
     * @param context The context in which the item was used.
     * @return An {@link InteractionResult} indicating the outcome of the action.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        BlockState originalState = level.getBlockState(pos);

        // Do not proceed if the block is air or indestructible.
        if (originalState.isAir() || originalState.getDestroySpeed(level, pos) < 0) {
            return InteractionResult.FAIL;
        }

        // Perform world modification only on the server side.
        if (!level.isClientSide()) {
            VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(level.dimension());
            if (physicsWorld == null) {
                VxMainClass.LOGGER.error("Could not find VxPhysicsWorld for the level!");
                return InteractionResult.FAIL;
            }

            VxBodyManager bodyManager = physicsWorld.getBodyManager();

            // Pre-check to ensure a valid physics shape can be generated from the block.
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
                // Replace the block with air, notifying neighbors to trigger updates.
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

                RVec3 position = new RVec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                Quat rotation = Quat.sIdentity();
                VxTransform transform = new VxTransform(position, rotation);

                // Create the rigid body in the physics world.
                BlockRigidBody body = bodyManager.createRigidBody(
                        VxRegisteredBodies.BLOCK,
                        transform,
                        EActivation.Activate,
                        b -> b.setRepresentedBlockState(originalState)
                );

                if (body == null) {
                    throw new IllegalStateException("Body creation returned null.");
                }

            } catch (Exception e) {
                VxMainClass.LOGGER.error("Error creating physics body, restoring original block.", e);
                // Restore the original block if body creation fails.
                level.setBlock(pos, originalState, Block.UPDATE_ALL);
                return InteractionResult.FAIL;
            }
        }

        // Return CONSUME to indicate success and stop further processing
        // without triggering the client-side hand swing animation.
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}