/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.boxlauncher;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/**
 * The Box Launcher item.
 * <p>
 * Logic is handled by {@link VxBoxLauncherMode}.
 *
 * @author xI-Mx-Ix
 */
public class VxBoxLauncherItem extends Item {

    public VxBoxLauncherItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.RARE));
    }

    @Override
    public boolean isFoil(ItemStack pStack) {
        return true;
    }
}