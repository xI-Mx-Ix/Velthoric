package net.xmx.xbullet.item;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.operator.Op;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
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
import net.xmx.xbullet.builtin.block.BlockRigidPhysicsObject;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.ObjectManager;
import net.xmx.xbullet.physics.object.rigidphysicsobject.builder.RigidPhysicsObjectBuilder;
import net.xmx.xbullet.physics.world.PhysicsWorld;

public class PhysicsCreatorItem extends Item {

    public PhysicsCreatorItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos blockPos = context.getClickedPos();
        BlockState clickedState = level.getBlockState(blockPos);

        if (!level.isClientSide()) {
            ServerLevel serverLevel = (ServerLevel) level;

            if (clickedState.isAir()) {
                return InteractionResult.PASS;
            }

            boolean removed = serverLevel.removeBlock(blockPos, false);

            if (removed) {
                RVec3 spawnPosition = new RVec3(
                        blockPos.getX() + 0.5,
                        blockPos.getY() + 0.5,
                        blockPos.getZ() + 0.5
                );

                Quat spawnRotation = extractRotationFromBlockState(clickedState);

                ObjectManager manager = PhysicsWorld.getObjectManager(serverLevel.dimension());
                if (manager == null || !manager.isInitialized()) {
                    XBullet.LOGGER.error("PhysicsObjectManager not initialized when trying to use PhysicsCreatorItem.");
                    serverLevel.setBlock(blockPos, clickedState, 3);
                    return InteractionResult.FAIL;
                }

                CompoundTag blockStateData = NbtUtils.writeBlockState(clickedState);
                CompoundTag customNbtData = new CompoundTag();
                customNbtData.put("blockState", blockStateData);

                var createdObject = new RigidPhysicsObjectBuilder()
                        .level(serverLevel)
                        .position(spawnPosition)
                        .rotation(spawnRotation)
                        .type(BlockRigidPhysicsObject.TYPE_IDENTIFIER)
                        .customNBTData(customNbtData)
                        .spawn();

                if (createdObject != null) {
                    serverLevel.playSound(null, blockPos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.0f, 1.0f);
                    if (context.getPlayer() != null && !context.getPlayer().getAbilities().instabuild) {
                        context.getItemInHand().shrink(1);
                    }
                    return InteractionResult.SUCCESS;
                } else {
                    XBullet.LOGGER.warn("Failed to create and register BlockPhysicsObject for state {} at {}. This might be due to an unregistered type or issues with the constructor.", clickedState, spawnPosition);
                    serverLevel.setBlock(blockPos, clickedState, 3);
                    return InteractionResult.FAIL;
                }
            } else {
                XBullet.LOGGER.warn("Failed to remove block at {} using PhysicsCreatorItem.", blockPos);
                return InteractionResult.FAIL;
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    private Quat extractRotationFromBlockState(BlockState blockState) {
        Quat combinedRotation = Quat.sIdentity();

        if (blockState.hasProperty(BlockStateProperties.AXIS)) {
            Direction.Axis axis = blockState.getValue(BlockStateProperties.AXIS);
            Quat axisRot = Quat.sEulerAngles(
                    axis == Direction.Axis.Z ? (float) Math.toRadians(90) : 0f,
                    0f,
                    axis == Direction.Axis.X ? (float) Math.toRadians(90) : 0f
            );
            combinedRotation = Op.star(axisRot, combinedRotation);
        }

        if (blockState.hasProperty(BlockStateProperties.FACING)) {
            Direction facing = blockState.getValue(BlockStateProperties.FACING);
            float yaw = switch (facing) {
                case NORTH -> (float) Math.PI;
                case WEST -> (float) Math.PI / 2f;
                case EAST -> -(float) Math.PI / 2f;
                default -> 0f;
            };
            float pitch = switch (facing) {
                case UP -> -(float) Math.PI / 2f;
                case DOWN -> (float) Math.PI / 2f;
                default -> 0f;
            };
            Quat facingRot = Quat.sEulerAngles(pitch, yaw, 0f);
            combinedRotation = Op.star(facingRot, combinedRotation);

        } else if (blockState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            Direction facing = blockState.getValue(BlockStateProperties.HORIZONTAL_FACING);
            float yaw = switch (facing) {
                case NORTH -> (float) Math.PI;
                case WEST -> (float) Math.PI / 2f;
                case EAST -> -(float) Math.PI / 2f;
                default -> 0f;
            };
            Quat facingRot = Quat.sEulerAngles(0f, yaw, 0f);
            combinedRotation = Op.star(facingRot, combinedRotation);
        }

        if (blockState.hasProperty(BlockStateProperties.ROTATION_16)) {
            int rotation = blockState.getValue(BlockStateProperties.ROTATION_16);
            Quat rot16 = Quat.sEulerAngles(0f, (float) Math.toRadians(rotation * -22.5), 0f);
            combinedRotation = Op.star(rot16, combinedRotation);
        }

        if (blockState.hasProperty(BlockStateProperties.ATTACH_FACE)) {
            var attachFace = blockState.getValue(BlockStateProperties.ATTACH_FACE);
            if (attachFace.toString().equals("ceiling")) {
                Quat ceilingRot = Quat.sEulerAngles((float) Math.PI, 0, 0);
                combinedRotation = Op.star(ceilingRot, combinedRotation);
            }
        }

        if (blockState.hasProperty(BlockStateProperties.HALF)) {
            var half = blockState.getValue(BlockStateProperties.HALF);
            if (half.toString().equals("top")) {
                Quat halfRot = Quat.sEulerAngles((float) Math.PI, 0, 0);
                combinedRotation = Op.star(halfRot, combinedRotation);
            }
        }

        return combinedRotation.normalized();
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}