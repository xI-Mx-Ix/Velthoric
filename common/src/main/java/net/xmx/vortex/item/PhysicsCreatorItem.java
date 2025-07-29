package net.xmx.vortex.item;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.operator.Op;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.xmx.vortex.builtin.VxRegisteredObjects;
import net.xmx.vortex.builtin.block.BlockRigidPhysicsObject;
import net.xmx.vortex.init.VxMainClass;
import net.xmx.vortex.math.VxTransform;
import net.xmx.vortex.physics.object.physicsobject.manager.VxObjectManager;
import net.xmx.vortex.physics.world.VxPhysicsWorld;

import java.util.Optional;

public class PhysicsCreatorItem extends Item {

    public PhysicsCreatorItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }

        BlockPos blockPos = context.getClickedPos();
        BlockState clickedState = level.getBlockState(blockPos);

        if (clickedState.isAir()) {
            return InteractionResult.PASS;
        }

        VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(serverLevel.dimension());
        if (physicsWorld == null) {
            VxMainClass.LOGGER.error("Physics world is not initialized in this dimension.");
            return InteractionResult.FAIL;
        }

        if (!serverLevel.removeBlock(blockPos, false)) {
            VxMainClass.LOGGER.warn("Failed to remove block at {} to create physics object.", blockPos);
            return InteractionResult.FAIL;
        }

        RVec3 spawnPosition = new RVec3(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
        Quat spawnRotation = extractRotationFromBlockState(clickedState);
        VxTransform transform = new VxTransform(spawnPosition, spawnRotation);

        VxObjectManager manager = physicsWorld.getObjectManager();

        Optional<BlockRigidPhysicsObject> spawnedObject = manager.spawnObject(
                VxRegisteredObjects.BLOCK,
                transform,
                block -> block.setRepresentedBlockState(clickedState)
        );

        if (spawnedObject.isPresent()) {
            level.playSound(null, blockPos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.0f, 1.0f);
            if (context.getPlayer() != null && !context.getPlayer().getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }
            return InteractionResult.SUCCESS;
        } else {
            VxMainClass.LOGGER.warn("Failed to spawn BlockRigidPhysicsObject. Restoring block at {}.", blockPos);
            serverLevel.setBlock(blockPos, clickedState, 3);
            return InteractionResult.FAIL;
        }
    }

    private Quat extractRotationFromBlockState(BlockState state) {

        Quat totalRotation = Quat.sIdentity();

        if (state.hasProperty(BlockStateProperties.AXIS)) {
            totalRotation = switch (state.getValue(BlockStateProperties.AXIS)) {
                case X -> new Quat(0f, 0f, 0.7071f, 0.7071f);
                case Z -> new Quat(0f, 0.7071f, 0f, 0.7071f);
                default -> Quat.sIdentity();
            };
        }

        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            float yaw = facing.toYRot();
            Quat facingRot = Quat.sEulerAngles(0, (float) Math.toRadians(yaw), 0);
            totalRotation = Op.star(facingRot, totalRotation);
        } else if (state.hasProperty(BlockStateProperties.FACING)) {

        }

        if (state.hasProperty(BlockStateProperties.ROTATION_16)) {
            int rotation = state.getValue(BlockStateProperties.ROTATION_16);
            float yaw = rotation * -22.5f;
            Quat rotation16 = Quat.sEulerAngles(0f, (float) Math.toRadians(yaw), 0f);
            totalRotation = Op.star(rotation16, totalRotation);
        }

        return totalRotation.normalized();
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}