/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.terrain.interaction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;
import net.xmx.velthoric.core.terrain.material.VxTerrainMaterial;

/**
 * Handles physical interactions triggered by the native Jolt ContactListener.
 * This class provides methods that are called directly from C++ via JNI.
 * <p>
 * All methods in this class are executed on the Minecraft server thread to ensure
 * thread-safe access to world data and block states.
 * </p>
 *
 * @author xI-Mx-Ix
 */
@SuppressWarnings("unused")
public class VxTerrainInteractionHandler {

    /**
     * Triggered when a fragile block exceeds its defined breaking threshold.
     * This method removes the block from the world and spawns breaking particles/sounds.
     *
     * @param world The physics world where the event occurred.
     * @param x     The world-space X-coordinate of the contact point.
     * @param y     The world-space Y-coordinate of the contact point.
     * @param z     The world-space Z-coordinate of the contact point.
     * @param force The estimated impact force that caused the break.
     */
    @SuppressWarnings("unused")
    public static void onBlockBreak(VxPhysicsWorld world, double x, double y, double z, float force) {
        ServerLevel level = world.getLevel();
        if (level == null) return;

        level.getServer().execute(() -> {
            BlockPos pos = findSolidBlockPos(level, x, y, z);
            if (level.getBlockState(pos).isAir()) return;

            // destroyBlock handles sound, particles, and block removal automatically
            level.destroyBlock(pos, true);
        });
    }

    /**
     * Helper method to accurately resolve the physical block involved in a physics contact.
     * <p>
     * Jolt Physics contact manifolds often report intersection points that lie exactly on the 
     * mathematical boundary between a solid block and an adjacent air block (e.g., resting on 
     * a floor at Y=64.0 resolves to the air block at Y=65).
     * </p>
     * <p>
     * This method compensates for floating-point inaccuracies and bounding box edges by 
     * systematically nudging the lookup coordinates downwards and sidewards until a solid 
     * block state is found.
     * </p>
     *
     * @param level The server level.
     * @param x     The raw X contact coordinate from the physics engine.
     * @param y     The raw Y contact coordinate from the physics engine.
     * @param z     The raw Z contact coordinate from the physics engine.
     * @return The exact {@link BlockPos} of the first discovered non-air block, or the original 
     *         position if no solid block was found within the immediate vicinity.
     */
    private static BlockPos findSolidBlockPos(ServerLevel level, double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y, z);
        if (!level.getBlockState(pos).isAir()) return pos;

        // The contact point is often exactly on the boundary between the block and air.
        pos = BlockPos.containing(x, y - 0.05, z);
        if (!level.getBlockState(pos).isAir()) return pos;

        // Nudge sideways for wall impacts
        BlockPos[] offsets = {
                BlockPos.containing(x - 0.05, y, z),
                BlockPos.containing(x + 0.05, y, z),
                BlockPos.containing(x, y, z - 0.05),
                BlockPos.containing(x, y, z + 0.05)
        };
        
        for (BlockPos offsetPos : offsets) {
            if (!level.getBlockState(offsetPos).isAir()) return offsetPos;
        }

        // Return the original air block pos if all else fails
        return BlockPos.containing(x, y, z);
    }

    /**
     * Spawns friction-based particles at a specific contact point.
     * The particle type is derived from the block state at the given position.
     *
     * @param world     The physics world where the particles should spawn.
     * @param x         The world X-coordinate of the contact point.
     * @param y         The world Y-coordinate of the contact point.
     * @param z         The world Z-coordinate of the contact point.
     * @param intensity The physical intensity of the sliding motion, used to scale particle count and sound volume.
     */
    @SuppressWarnings("unused")
    public static void onSpawnParticles(VxPhysicsWorld world, float x, float y, float z, float intensity) {
        ServerLevel level = world.getLevel();
        if (level == null) return;

        level.getServer().execute(() -> {
            BlockPos pos = findSolidBlockPos(level, x, y, z);
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) return;

            // Audio Feedback
            // Volume scaling is now extremely harsh. Only massive energy leads to loud sounds.
            float volume = Math.min(1.0f, (intensity * intensity * 0.15f) + (intensity * 0.05f));

            // To prevent low-volume humming/spam, we drop very quiet sounds probabilistically.
            // E.g. volume 0.05 -> 50% drop rate. volume 0.01 -> 90% drop rate.
            boolean playSound = true;
            if (volume < 0.1f) {
                if (level.random.nextFloat() > (volume * 10.0f)) {
                    playSound = false;
                }
            }

            if (playSound) {
                float pitch = 0.8f + level.random.nextFloat() * 0.4f;
                level.playSound(null, (double)x, (double)y, (double)z, 
                        state.getSoundType().getHitSound(), SoundSource.BLOCKS, 
                        volume, pitch);
            }

            // Visual Particles
            // Scaled way down. Requires very high intensity to generate many particles.
            int baseParticles = (int) (intensity * 1.0f); 
            
            // For low intensity, occasionally spawn 1 particle (30% chance) to show activity.
            if (baseParticles < 1 && intensity > 0.05f) {
                if (level.random.nextFloat() < 0.3f) {
                    baseParticles = 1;
                }
            }

            // Cap to avoid visual clutter even on heavy impacts
            baseParticles = Math.min(baseParticles, 20);

            if (baseParticles > 0) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                        x, y, z, baseParticles, 0.1, 0.1, 0.1, 0.05);
            }
        });
    }

    /**
     * Handles physical interactions with interactive blocks (Doors, Trapdoors, Fence Gates).
     * This method is invoked by the native C++ physics engine when a dynamic body collides 
     * with a block flagged as {@code interactable} in the material registry.
     * <p>
     * The logic ensures that blocks only react to physically plausible impacts:
     * <ul>
     *     <li><b>Doors:</b> Only open if pushed against their front face. Side-swipes are ignored.</li>
     *     <li><b>Trapdoors:</b> Bottom-half trapdoors only open if struck from below. Top-half 
     *         trapdoors only open if struck from above.</li>
     *     <li><b>Fence Gates:</b> Only swing open if struck perpendicularly (moving through the opening).
     *         Hitting a gate's side post will not rotate or break its structural axis. If already open,
     *         striking the gate from the opposite side will snap it closed.</li>
     *     <li><b>Iron Variants:</b> Explicitly protected from physical nudges, requiring Redstone.</li>
     * </ul>
     * </p>
     *
     * @param world The Velthoric physics world where the event occurred.
     * @param x     The world-space X-coordinate of the contact point.
     * @param y     The world-space Y-coordinate of the contact point.
     * @param z     The world-space Z-coordinate of the contact point.
     * @param nX    The collision normal X-component (pointing from block to impactor).
     * @param nY    The collision normal Y-component.
     * @param nZ    The collision normal Z-component.
     */
    @SuppressWarnings("unused")
    public static void onBlockInteract(VxPhysicsWorld world, double x, double y, double z, float nX, float nY, float nZ) {
        ServerLevel level = world.getLevel();
        if (level == null) return;

        level.getServer().execute(() -> {
            BlockPos pos = findSolidBlockPos(level, x, y, z);
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) return;

            Block block = state.getBlock();

            // Protection: Ignore iron variants as they require Redstone
            if (block == Blocks.IRON_DOOR || block == Blocks.IRON_TRAPDOOR) {
                return;
            }

            if (block instanceof DoorBlock) {
                boolean isOpen = state.getValue(BlockStateProperties.OPEN);
                Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
                
                // Normal points from block to body.
                // dot > 0 means the body is on the side the door faces (the front/outside).
                // dot < 0 means the body is on the opposite side (the back/inside).
                float dotProduct = nX * facing.getStepX() + nZ * facing.getStepZ();

                if (!isOpen) {
                    // Only open if hit from the front (outside)
                    if (dotProduct > 0.4f) { 
                        level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, true), 10);
                        level.levelEvent(null, 1006, pos, 0); 
                    }
                } else {
                    // Only close if hit from the back (inside)
                    if (dotProduct < -0.4f) {
                        level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, false), 10);
                        level.levelEvent(null, 1012, pos, 0); 
                    }
                }
            } else if (block instanceof TrapDoorBlock) {
                boolean isOpen = state.getValue(BlockStateProperties.OPEN);
                if (state.hasProperty(BlockStateProperties.HALF)) {
                    boolean isTop = state.getValue(BlockStateProperties.HALF) == net.minecraft.world.level.block.state.properties.Half.TOP;
                    
                    // For trapdoors, we check if we hit the 'outside' face.
                    // Top-half: outside is ABOVE (normal.y > 0).
                    // Bottom-half: outside is BELOW (normal.y < 0).
                    float outsideDir = isTop ? nY : -nY;

                    if (!isOpen) {
                        // Open if hit from outside
                        if (outsideDir > 0.4f) {
                            level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, true), 10);
                            level.levelEvent(null, 1006, pos, 0); 
                        }
                    } else {
                        // Close if hit from inside
                        if (outsideDir < -0.4f) {
                            level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, false), 10);
                            level.levelEvent(null, 1012, pos, 0); 
                        }
                    }
                }
            } else if (block instanceof FenceGateBlock) {
                if (!state.getValue(BlockStateProperties.OPEN)) {
                    // Open away from the impact (normal points towards impact, so open in opposite direction)
                    Direction openDir = Direction.getNearest(-nX, 0, -nZ);
                    level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, true)
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, openDir), 10);
                    level.levelEvent(null, 1008, pos, 0);
                }
            }
        });
    }

    /**
     * Transforms certain terrain blocks into another block type when subject to extreme sliding friction or pressure.
     * This simulates the wearing down of topsoil, vegetation, or structural surfaces by physical pressure.
     * The transformation target is dynamically looked up from the material properties of the block at the position.
     *
     * @param world    The physics world where the transformation should occur.
     * @param x        The world-space X-coordinate of the contact point.
     * @param y        The world-space Y-coordinate of the contact point.
     * @param z        The world-space Z-coordinate of the contact point.
     * @param force    The physical strength/force applied to the block.
     */
    @SuppressWarnings("unused")
    public static void onTerrainTransform(VxPhysicsWorld world, double x, double y, double z, float force) {
        ServerLevel level = world.getLevel();
        if (level == null) return;

        level.getServer().execute(() -> {
            BlockPos pos = findSolidBlockPos(level, x, y, z);
            BlockState state = level.getBlockState(pos);
            
            // Lookup the transformation target for this specific block type
            VxTerrainMaterial.MaterialProperties props = VxTerrainMaterial.getProperties(state.getBlock());

            // Trigger if the material is transformable and physical conditions are met
            if (props.isTransformable() && force > 500.0f) {
                level.setBlockAndUpdate(pos, props.transformTo.defaultBlockState());
            }
        });
    }
}