/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.builtin.box;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Random;

/**
 * @author xI-Mx-Ix
 */
public enum BoxColor {
    WHITE(Blocks.WHITE_CONCRETE),
    ORANGE(Blocks.ORANGE_CONCRETE),
    MAGENTA(Blocks.MAGENTA_CONCRETE),
    LIGHT_BLUE(Blocks.LIGHT_BLUE_CONCRETE),
    YELLOW(Blocks.YELLOW_CONCRETE),
    LIME(Blocks.LIME_CONCRETE),
    PINK(Blocks.PINK_CONCRETE),
    GRAY(Blocks.GRAY_CONCRETE),
    LIGHT_GRAY(Blocks.LIGHT_GRAY_CONCRETE),
    CYAN(Blocks.CYAN_CONCRETE),
    PURPLE(Blocks.PURPLE_CONCRETE),
    BLUE(Blocks.BLUE_CONCRETE),
    BROWN(Blocks.BROWN_CONCRETE),
    GREEN(Blocks.GREEN_CONCRETE),
    RED(Blocks.RED_CONCRETE),
    BLACK(Blocks.BLACK_CONCRETE);

    private final Block block;
    private static final BoxColor[] VALUES = values();
    private static final Random RANDOM = new Random();

    BoxColor(Block block) {
        this.block = block;
    }

    public Block getBlock() {
        return block;
    }

    public static BoxColor getRandom() {
        return VALUES[RANDOM.nextInt(VALUES.length)];
    }
}
