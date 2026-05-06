/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.terrain.interaction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.Half;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;
import net.xmx.velthoric.core.terrain.material.VxTerrainMaterial;
import net.xmx.velthoric.core.terrain.material.VxTerrainMaterial.MaterialProperties;
import net.xmx.velthoric.jni.TerrainInteraction;

import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Handles physical interactions triggered by the native Jolt ContactListener.
 * This class provides methods that are called directly from C++ via JNI.
 * <p>
 * Interactions are queued and processed on the Minecraft server thread to ensure
 * thread-safe access to world data and prevent deadlocks during shutdown.
 * </p>
 *
 * @author xI-Mx-Ix
 */
@SuppressWarnings("unused")
public class VxTerrainInteractionHandler {

    /**
     * The maximum number of visual/audio level events (particles/sounds) to trigger per tick.
     * Prevents network packet flooding and server-thread stalls.
     */
    private static final int MAX_EFFECTS_PER_TICK = 32;

    /**
     * The maximum number of Java-side interaction events to process in a single tick.
     */
    private static final int MAX_INTERACTIONS_PER_TICK = 512;

    // Minecraft Level Event IDs

    /**
     * Sound and particles for a wooden door opening.
     */
    private static final int EVENT_WOODEN_DOOR_OPEN = 1006;
    /**
     * Sound for a wooden door closing.
     */
    private static final int EVENT_WOODEN_DOOR_CLOSE = 1012;
    /**
     * Heavy impact sound (Zombie hitting door).
     */
    private static final int EVENT_ZOMBIE_ATTACK_WOODEN_DOOR = 1019;
    /**
     * Sound for a fence gate opening.
     */
    private static final int EVENT_FENCE_GATE_OPEN = 1008;
    /**
     * Particle and sound effect for block destruction.
     */
    private static final int EVENT_BLOCK_BREAK = 2001;

    /**
     * Internal thread-safe queue for manual interaction events submitted from Java.
     */
    private static final Queue<InteractionEvent> eventQueue = new ConcurrentLinkedQueue<>();

    /**
     * Processes interaction events from both Java and Native layers.
     * <p>
     * This method is called on the Minecraft server thread once per tick. It drains both
     * the internal Java event queue and the sharded native buffers to ensure all
     * physical feedback is applied synchronously within the game loop.
     * </p>
     *
     * @param world The Velthoric physics world.
     */
    public static void tick(VxPhysicsWorld world) {
        // 1. Process Java-side manual events (Throttled)
        for (int i = 0; i < MAX_INTERACTIONS_PER_TICK; i++) {
            InteractionEvent event = eventQueue.poll();
            if (event == null) break;
            event.handle(world);
        }

        // 2. Process Native-side physics events (Zero-allocation batch)
        Set<BlockPos> touchedPositions = new HashSet<>();
        int[] effectCount = {0}; // Mutable wrapper for lambda

        TerrainInteraction.processEvents((type, matId, x1, y1, z1, x2, y2, z2, strength, subShapeId, terrainBodyId) -> {
            handleNativeEvent(world, type, matId, x1, y1, z1, x2, y2, z2, strength, subShapeId, terrainBodyId, touchedPositions, effectCount);
        });
    }

    /**
     * Dispatches a native interaction event to the appropriate handler logic.
     * <p>
     * Native events are generated in the C++ physics threads and batched into
     * sharded queues. This method unpacks those events and executes the
     * corresponding gameplay effects (destruction, sounds, etc.) on the server thread.
     * </p>
     *
     * @param world            The Velthoric physics world.
     * @param type             The category of interaction.
     * @param matId            The material ID of the terrain block.
     * @param x1,              y1, z1       Primary coordinates (usually the block center).
     * @param x2,              y2, z2       Secondary coordinates (contact point or normal).
     * @param strength         The physical intensity of the impact.
     * @param subShapeId       Precise Jolt sub-shape identifier.
     * @param terrainBodyId    The native ID of the terrain body.
     * @param touchedPositions A set used for per-tick spatial deduplication.
     * @param effectCount      A mutable counter to enforce visual/audio budgets.
     */
    private static void handleNativeEvent(VxPhysicsWorld world, TerrainInteraction.InteractionType type,
                                          int matId, float x1, float y1, float z1, float x2, float y2, float z2,
                                          float strength, int subShapeId, int terrainBodyId,
                                          java.util.Set<BlockPos> touchedPositions, int[] effectCount) {
        ServerLevel level = world.getLevel();
        if (level == null) return;

        BlockPos pos = BlockPos.containing(x1, y1, z1);

        // Check if the chunk is loaded and ready.
        if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
            return;
        }

        switch (type) {
            case BLOCK_BREAK -> {
                if (!touchedPositions.add(pos.immutable())) return;
                onTerrainBreak(world, x1, y1, z1, strength, effectCount[0]++ < MAX_EFFECTS_PER_TICK);
            }
            case BLOCK_TRANSFORM -> {
                if (!touchedPositions.add(pos.immutable())) return;
                onTerrainTransform(world, x1, y1, z1, strength);
            }
            case BLOCK_INTERACT -> {
                if (!touchedPositions.add(pos.immutable())) return;
                onBlockInteract(world, x1, y1, z1, x2, y2, z2);
            }
            case TERRAIN_SLIDE -> {
                if (!touchedPositions.add(pos.immutable())) return;
                if (effectCount[0]++ < MAX_EFFECTS_PER_TICK) {
                    onTerrainSlide(world, x1, y1, z1, x2, y2, z2, strength);
                }
            }
            case TERRAIN_IMPACT -> {
                if (effectCount[0]++ < MAX_EFFECTS_PER_TICK) {
                    onTerrainImpact(world, x1, y1, z1, x2, y2, z2, strength);
                }
            }
        }
    }

    /**
     * Triggers the destruction of a fragile terrain block.
     *
     * @param world   The physics world.
     * @param x       World X-coordinate.
     * @param y       World Y-coordinate.
     * @param z       World Z-coordinate.
     * @param force   The force applied to the block.
     * @param effects Whether to spawn particles/sounds.
     */
    public static void onTerrainBreak(VxPhysicsWorld world, double x, double y, double z, float force, boolean effects) {
        ServerLevel level = world.getLevel();
        if (level == null) return;

        BlockPos pos = BlockPos.containing(x, y, z);
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return;

        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16 | 32);
        if (effects) {
            level.levelEvent(null, EVENT_BLOCK_BREAK, pos, Block.getId(state));
        }
    }

    /**
     * Transforms certain terrain blocks into another block type when subject to extreme pressure.
     *
     * @param world The physics world.
     * @param x     World X-coordinate.
     * @param y     World Y-coordinate.
     * @param z     World Z-coordinate.
     * @param force The physical strength/force applied.
     */
    public static void onTerrainTransform(VxPhysicsWorld world, double x, double y, double z, float force) {
        ServerLevel level = world.getLevel();
        if (level == null) return;

        BlockPos pos = BlockPos.containing(x, y, z);
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return;

        MaterialProperties props = VxTerrainMaterial.getProperties(state.getBlock());
        if (props.isTransformable() && force > 500.0f) {
            level.setBlock(pos, props.transformTo.defaultBlockState(), 2 | 16 | 32);
        }
    }

    /**
     * Handles physical interactions with interactive blocks (Doors, Trapdoors, Fence Gates).
     *
     * @param world The Velthoric physics world.
     * @param x     The world-space X-coordinate.
     * @param y     The world-space Y-coordinate.
     * @param z     The world-space Z-coordinate.
     * @param nX    The collision normal X-component.
     * @param nY    The collision normal Y-component.
     * @param nZ    The collision normal Z-component.
     */
    public static void onBlockInteract(VxPhysicsWorld world, double x, double y, double z, float nX, float nY, float nZ) {
        ServerLevel level = world.getLevel();
        if (level == null) return;

        BlockPos pos = BlockPos.containing(x, y, z);
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return;

        Block block = state.getBlock();

        // Protection: Ignore iron variants as they require Redstone
        if (block == Blocks.IRON_DOOR || block == Blocks.IRON_TRAPDOOR) return;

        if (block instanceof DoorBlock) {
            boolean isOpen = state.getValue(BlockStateProperties.OPEN);
            Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            // Normal points from body to door.
            float dotProduct = nX * facing.getStepX() + nZ * facing.getStepZ();

            if (!isOpen) {
                // Open if pushed away from its placement edge into the block
                if (dotProduct > 0.4f) {
                    level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, true), 10);
                    level.levelEvent(null, EVENT_ZOMBIE_ATTACK_WOODEN_DOOR, pos, 0);
                }
            } else {
                // For open doors, we check if the impact is on the open face pushing it towards the frame.
                // RIGHT hinge rotates 90 deg clockwise, LEFT hinge rotates 90 deg counter-clockwise.
                DoorHingeSide hinge = state.getValue(BlockStateProperties.DOOR_HINGE);
                Direction openFacing = (hinge == DoorHingeSide.RIGHT) ? facing.getClockWise() : facing.getCounterClockWise();
                float closeDot = nX * openFacing.getStepX() + nZ * openFacing.getStepZ();

                if (closeDot < -0.4f) {
                    level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, false), 10);
                    level.levelEvent(null, EVENT_ZOMBIE_ATTACK_WOODEN_DOOR, pos, 0);
                }
            }
        } else if (block instanceof TrapDoorBlock) {
            boolean isOpen = state.getValue(BlockStateProperties.OPEN);
            if (state.hasProperty(BlockStateProperties.HALF)) {
                boolean isTop = state.getValue(BlockStateProperties.HALF) == Half.TOP;
                float outsideDir = isTop ? nY : -nY;

                if (!isOpen) {
                    if (outsideDir > 0.4f) {
                        level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, true), 10);
                        level.levelEvent(null, EVENT_WOODEN_DOOR_OPEN, pos, 0);
                    }
                } else {
                    // If open, it's standing up/down. Close if hit horizontally.
                    if (Math.abs(nY) < 0.2f && (Math.abs(nX) > 0.4f || Math.abs(nZ) > 0.4f)) {
                        level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, false), 10);
                        level.levelEvent(null, EVENT_WOODEN_DOOR_CLOSE, pos, 0);
                    }
                }
            }
        } else if (block instanceof FenceGateBlock) {
            if (!state.getValue(BlockStateProperties.OPEN)) {
                // Fence gates open away from the impact
                Direction openDir = Direction.getNearest(-nX, 0, -nZ);
                level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, true)
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, openDir), 10);
                level.levelEvent(null, EVENT_FENCE_GATE_OPEN, pos, 0);
            }
        }
    }

    /**
     * Spawns friction-based particles at a specific contact point.
     *
     * @param world     The physics world.
     * @param blockX    The block X-coordinate.
     * @param blockY    The block Y-coordinate.
     * @param blockZ    The block Z-coordinate.
     * @param contactX  The world X-coordinate of the contact.
     * @param contactY  The world Y-coordinate of the contact.
     * @param contactZ  The world Z-coordinate of the contact.
     * @param intensity The physical intensity of the sliding motion.
     */
    public static void onTerrainSlide(VxPhysicsWorld world, double blockX, double blockY, double blockZ, float contactX, float contactY, float contactZ, float intensity) {
        ServerLevel level = world.getLevel();
        if (level == null) return;

        BlockPos pos = BlockPos.containing(blockX, blockY, blockZ);
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return;

        // Logarithmic volume scaling
        float volume = Math.min(0.15f, 0.01f + 0.04f * (float) Math.log1p(intensity));
        if (volume < 0.03f && level.random.nextFloat() > (volume * 20.0f)) return;

        float pitch = 0.8f + level.random.nextFloat() * 0.4f;
        level.playSound(null, contactX, contactY, contactZ,
                state.getSoundType().getHitSound(), SoundSource.BLOCKS, volume, pitch);

        // Visual Particles
        int baseParticles = Math.min(20, (int) (intensity * 1.0f));
        if (baseParticles < 1 && intensity > 0.05f && level.random.nextFloat() < 0.3f) baseParticles = 1;

        if (baseParticles > 0) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    contactX, contactY, contactZ, baseParticles, 0.1, 0.1, 0.1, 0.05);
        }
    }

    /**
     * Spawns high-intensity impact particles and a loud sound at the contact point.
     * <p>
     * This method is triggered once per contact-point when a body first hits terrain
     * with significant normal velocity. Volume and particle count scale with impact energy.
     * </p>
     *
     * @param world     The physics world.
     * @param blockX    The block X-coordinate.
     * @param blockY    The block Y-coordinate.
     * @param blockZ    The block Z-coordinate.
     * @param contactX  The world X-coordinate of the contact.
     * @param contactY  The world Y-coordinate of the contact.
     * @param contactZ  The world Z-coordinate of the contact.
     * @param intensity The physical intensity of the impact.
     */
    public static void onTerrainImpact(VxPhysicsWorld world, double blockX, double blockY, double blockZ, float contactX, float contactY, float contactZ, float intensity) {
        ServerLevel level = world.getLevel();
        if (level == null) return;

        BlockPos pos = BlockPos.containing(blockX, blockY, blockZ);
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return;

        // Logarithmic volume
        float volume = Math.min(0.3f, 0.02f + 0.06f * (float) Math.log1p(intensity));
        float pitch = 0.65f + level.random.nextFloat() * 0.25f;
        level.playSound(null, contactX, contactY, contactZ,
                state.getSoundType().getHitSound(), SoundSource.BLOCKS, volume, pitch);

        // Heavy particle burst
        int particles = Math.min(40, 4 + (int) (intensity * 3.0f));
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                contactX, contactY, contactZ, particles, 0.2, 0.15, 0.2, 0.1);
    }
}