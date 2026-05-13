/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.weldtool;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/**
 * The Weld Tool item.
 * <p>
 * Logic is handled by {@link VxWeldToolMode}.
 *
 * @author xI-Mx-Ix
 */
public class VxWeldToolItem extends Item {

    public VxWeldToolItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    @Override
    public boolean isFoil(ItemStack pStack) {
        return true;
    }
}
