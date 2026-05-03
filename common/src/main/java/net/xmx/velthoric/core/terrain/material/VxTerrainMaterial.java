/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.terrain.material;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.xmx.velthoric.jni.TerrainGenerator;
import net.xmx.velthoric.jni.TerrainInteraction;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * The {@code VxTerrainMaterial} class serves as a centralized registry for physical material
 * properties within the Velthoric engine. It maps Minecraft {@link Block} instances to specific
 * physical attributes such as friction, restitution (bounciness), and weight.
 * <p>
 * This registry synchronizes data between the Java-side world management and the native Jolt
 * Physics simulation. It also handles interaction-specific metadata like fragility and
 * transformation capabilities.
 * </p>
 *
 * @author xI-Mx-Ix
 */
public class VxTerrainMaterial {
    /**
     * Internal mapping from Minecraft blocks to their registered physical properties.
     * Uses an {@link IdentityHashMap} for maximum performance during lookups.
     */
    private static final Map<Block, MaterialProperties> blockMaterials = new IdentityHashMap<>();

    /**
     * Default fallback properties used when a block is not explicitly registered.
     */
    private static final MaterialProperties DEFAULT = new MaterialProperties((short) 1, 0.75f, 0.0f, 100.0f, false, null, true, 5000.0f, false);

    /**
     * The next available unique material ID. ID 0 is reserved for air, ID 1 for default.
     */
    private static short nextId = 2;

    /**
     * Data container for a block's physical and interactive properties.
     */
    public static class MaterialProperties {
        /**
         * Unique 16-bit identifier for the material, used in the native physics layers.
         */
        public final short id;
        /**
         * Sliding friction coefficient.
         */
        public final float friction;
        /**
         * Coefficient of restitution (0.0 = no bounce, 1.0 = perfect elastic collision).
         */
        public final float restitution;
        /**
         * Physical weight/density of the block in kg/m³.
         */
        public final float weight;
        /**
         * If true, the block can be destroyed if physical force exceeds the {@code breakThreshold}.
         */
        public final boolean isFragile;
        /**
         * The block this material transforms into under pressure. Null if not transformable.
         */
        public final Block transformTo;
        /**
         * If true, sliding against this material will spawn visual particles.
         */
        public final boolean spawnsParticles;
        /**
         * The threshold (impact force or static pressure) required to break this material.
         */
        public final float breakThreshold;
        /**
         * If true, the block can be interacted with (e.g. doors, trapdoors opening) when hit.
         */
        public final boolean isInteractable;

        /**
         * Internal constructor for material properties.
         *
         * @param id              Unique material ID.
         * @param friction        Sliding friction.
         * @param restitution     Bounciness.
         * @param weight          Density.
         * @param isFragile       Destructibility.
         * @param transformTo     Transformation target.
         * @param spawnsParticles Visual effects flag.
         * @param isInteractable  Interaction flag.
         */
        public MaterialProperties(short id, float friction, float restitution, float weight,
                                  boolean isFragile, Block transformTo, boolean spawnsParticles, float breakThreshold, boolean isInteractable) {
            this.id = id;
            this.friction = friction;
            this.restitution = restitution;
            this.weight = weight;
            this.isFragile = isFragile;
            this.transformTo = transformTo;
            this.spawnsParticles = spawnsParticles;
            this.breakThreshold = breakThreshold;
            this.isInteractable = isInteractable;
        }

        /**
         * Returns true if this material has a valid transformation target.
         */
        public boolean isTransformable() {
            return transformTo != null;
        }
    }

    /**
     * Registers a new physics material for a specific block type.
     * <p>
     * This method automatically handles the synchronization with the native Jolt Physics system.
     * Any previous registration for the same block will be overwritten.
     * </p>
     *
     * @param blockId         The resource location of the Minecraft block.
     * @param friction        The sliding friction value.
     * @param restitution     The coefficient of restitution.
     * @param weight          The density of the block.
     * @param isFragile       Whether the block should be destructible by physics.
     * @param transformTo     The block type this material transforms into under pressure. Use null for none.
     * @param spawnsParticles Whether to spawn friction particles.
     * @param breakThreshold  The force required to trigger block destruction.
     * @param isInteractable  Whether the block responds to physical nudges (doors opening).
     * @throws IllegalStateException If the maximum number of material IDs (65535) is exceeded.
     */
    public static void register(ResourceLocation blockId, float friction, float restitution, float weight,
                                boolean isFragile, Block transformTo, boolean spawnsParticles, float breakThreshold, boolean isInteractable) {
        if (nextId == 0) { // wraps around at 65536
            throw new IllegalStateException("Maximum number of terrain materials (65535) reached.");
        }
        Block block = BuiltInRegistries.BLOCK.get(blockId);
        if (block == Blocks.AIR) {
            return; // Ignore invalid or air blocks
        }

        short id = nextId++;
        MaterialProperties props = new MaterialProperties(id, friction, restitution, weight, isFragile, transformTo, spawnsParticles, breakThreshold, isInteractable);
        blockMaterials.put(block, props);

        // Register for standard collision properties in the native layer
        TerrainGenerator.nRegisterMaterial(id, friction, restitution);

        // Register for advanced interaction properties in the native layer
        TerrainInteraction.MaterialConfig config = new TerrainInteraction.MaterialConfig();
        config.matId = id;
        config.isFragile = isFragile;
        config.isTransformable = props.isTransformable();
        config.spawnsParticles = spawnsParticles;
        config.breakThreshold = breakThreshold;
        config.isInteractable = isInteractable;
        TerrainInteraction.registerMaterials(new TerrainInteraction.MaterialConfig[]{config});
    }

    /**
     * Simplified registration for blocks that do not require advanced interaction logic.
     *
     * @param blockId     The resource location of the Minecraft block.
     * @param friction    The sliding friction value.
     * @param restitution The coefficient of restitution.
     * @param weight      The density of the block.
     */
    public static void register(ResourceLocation blockId, float friction, float restitution, float weight) {
        register(blockId, friction, restitution, weight, false, null, true, 5000.0f, false);
    }

    /**
     * Retrieves the native material identifier for a given block.
     *
     * @param block The block to look up.
     * @return The 16-bit material ID, or 1 (default) if the block is not registered.
     */
    public static short getMaterialId(Block block) {
        MaterialProperties props = blockMaterials.get(block);
        return props != null ? props.id : 1;
    }

    /**
     * Retrieves the full physical and interactive properties for a specific block.
     *
     * @param block The block to look up.
     * @return The {@link MaterialProperties} for the block, or {@code DEFAULT} if not registered.
     */
    public static MaterialProperties getProperties(Block block) {
        MaterialProperties props = blockMaterials.get(block);
        return props != null ? props : DEFAULT;
    }

    /**
     * Clears all registered materials and resets the ID sequence.
     * <p>
     * This is typically called during a resource pack reload or world unload.
     * It also resets the default material configuration in the native layer.
     * </p>
     */
    public static void clear() {
        blockMaterials.clear();
        nextId = 2;

        // Re-initialize default material properties (ID 1) in native
        TerrainInteraction.MaterialConfig defaultMat = new TerrainInteraction.MaterialConfig();
        defaultMat.matId = 1;
        defaultMat.spawnsParticles = true;
        TerrainInteraction.registerMaterials(new TerrainInteraction.MaterialConfig[]{defaultMat});
    }
}