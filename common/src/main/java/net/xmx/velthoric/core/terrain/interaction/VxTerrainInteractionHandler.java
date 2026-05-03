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
     * @param x     The X-coordinate of the block.
     * @param y     The Y-coordinate of the block.
     * @param z     The Z-coordinate of the block.
     * @param force The estimated impact force that caused the break.
     */
    public static void onBlockBreak(VxPhysicsWorld world, int x, int y, int z, float force) {
        ServerLevel level = world.getLevel();
        if (level == null) return;

        BlockPos pos = new BlockPos(x, y, z);
        level.getServer().execute(() -> {
            if (level.getBlockState(pos).isAir()) return;

            // destroyBlock handles sound, particles, and block removal automatically
            level.destroyBlock(pos, true);
        });
    }

    private static BlockState findSolidBlock(ServerLevel level, double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y, z);
        BlockState state = level.getBlockState(pos);
        if (!state.isAir()) return state;

        // The contact point is often exactly on the boundary between the block and air.
        // For example, resting on top of y=64 gives contact point y=65.0, which resolves to air.
        // Nudge downwards first (most common: top surface impacts/sliding)
        pos = BlockPos.containing(x, y - 0.05, z);
        state = level.getBlockState(pos);
        if (!state.isAir()) return state;

        // Nudge sideways for wall impacts
        BlockPos[] offsets = {
                BlockPos.containing(x - 0.05, y, z),
                BlockPos.containing(x + 0.05, y, z),
                BlockPos.containing(x, y, z - 0.05),
                BlockPos.containing(x, y, z + 0.05)
        };
        
        for (BlockPos offsetPos : offsets) {
            state = level.getBlockState(offsetPos);
            if (!state.isAir()) return state;
        }

        // Return the original air block if all else fails
        return level.getBlockState(BlockPos.containing(x, y, z));
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
    public static void onSpawnParticles(VxPhysicsWorld world, float x, float y, float z, float intensity) {
        ServerLevel level = world.getLevel();
        if (level == null) return;

        level.getServer().execute(() -> {
            BlockState state = findSolidBlock(level, x, y, z);
            if (state.isAir()) return;

            // --- Audio Feedback ---
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

            // --- Visual Particles ---
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
     * Transforms certain terrain blocks into another block type when subject to extreme sliding friction or pressure.
     * This simulates the wearing down of topsoil, vegetation, or structural surfaces by physical pressure.
     * The transformation target is dynamically looked up from the material properties of the block at the position.
     *
     * @param world    The physics world where the transformation should occur.
     * @param x        The X-coordinate of the target block.
     * @param y        The Y-coordinate of the target block.
     * @param z        The Z-coordinate of the target block.
     * @param strength The physical strength/force applied to the block.
     */
    @SuppressWarnings("unused")
    public static void onTerrainTransform(VxPhysicsWorld world, int x, int y, int z, float strength) {
        ServerLevel level = world.getLevel();
        if (level == null) return;

        BlockPos pos = new BlockPos(x, y, z);
        level.getServer().execute(() -> {
            BlockState state = level.getBlockState(pos);
            
            // Lookup the transformation target for this specific block type
            VxTerrainMaterial.MaterialProperties props = VxTerrainMaterial.getProperties(state.getBlock());

            // Trigger if the material is transformable and physical conditions are met
            if (props.isTransformable() && strength > 500.0f) {
                level.setBlockAndUpdate(pos, props.transformTo.defaultBlockState());
            }
        });
    }
}