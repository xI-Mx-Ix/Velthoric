/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.item.magnetizer;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/**
 * The Magnetizer item.
 * <p>
 * Logic is handled by {@link VxMagnetizerMode}.
 *
 * @author xI-Mx-Ix
 */
public class VxMagnetizerItem extends Item {

    public VxMagnetizerItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    @Override
    public boolean isFoil(ItemStack pStack) {
        return true;
    }
}