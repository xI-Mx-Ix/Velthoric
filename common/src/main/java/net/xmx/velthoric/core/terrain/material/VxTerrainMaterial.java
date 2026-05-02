/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.terrain.material;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.xmx.velthoric.jni.TerrainGenerator;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Registry for terrain physics materials, allowing blocks to have custom friction, restitution, and weight natively.
 *
 * @author xI-Mx-Ix
 */
public class VxTerrainMaterial {
    private static final Map<Block, MaterialProperties> blockMaterials = new IdentityHashMap<>();
    private static final MaterialProperties DEFAULT = new MaterialProperties((short) 1, 0.75f, 0.0f, 100.0f);
    private static short nextId = 2; // 0 is air, 1 is default material

    public static class MaterialProperties {
        public final short id;
        public final float friction;
        public final float restitution;
        public final float weight;

        public MaterialProperties(short id, float friction, float restitution, float weight) {
            this.id = id;
            this.friction = friction;
            this.restitution = restitution;
            this.weight = weight;
        }
    }

    /**
     * Registers a physics material for a given block.
     * @param blockId The namespaced ID of the block.
     * @param friction The friction coefficient (e.g., 0.0 for ice).
     * @param restitution The restitution/bounciness (e.g., 0.8 for slime).
     * @param weight The mass/weight of the block (useful for block rigid bodies).
     */
    public static void register(ResourceLocation blockId, float friction, float restitution, float weight) {
        if (nextId == 0) { // wraps around at 65536
            throw new IllegalStateException("Maximum number of terrain materials (65535) reached.");
        }
        Block block = BuiltInRegistries.BLOCK.get(blockId);
        if (block == Blocks.AIR) {
            return; // Ignore invalid blocks
        }

        short id = nextId++;
        MaterialProperties props = new MaterialProperties(id, friction, restitution, weight);
        blockMaterials.put(block, props);
        TerrainGenerator.nRegisterMaterial(id, friction, restitution);
    }

    /**
     * Gets the material ID for a block. Returns 1 (default material) if not explicitly registered.
     */
    public static short getMaterialId(Block block) {
        MaterialProperties props = blockMaterials.get(block);
        return props != null ? props.id : 1;
    }

    /**
     * Gets the full material properties for a block, useful for block rigid bodies.
     */
    public static MaterialProperties getProperties(Block block) {
        MaterialProperties props = blockMaterials.get(block);
        return props != null ? props : DEFAULT;
    }

    /**
     * Clears the material registry (useful for reloading).
     */
    public static void clear() {
        blockMaterials.clear();
        nextId = 2;
    }
}