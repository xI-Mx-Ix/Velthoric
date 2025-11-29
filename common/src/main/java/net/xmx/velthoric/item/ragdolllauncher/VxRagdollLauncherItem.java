/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.ragdolllauncher;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/**
 * The Ragdoll Launcher item.
 * <p>
 * Logic is handled by {@link VxRagdollLauncherMode}.
 *
 * @author xI-Mx-Ix
 */
public class VxRagdollLauncherItem extends Item {

    public VxRagdollLauncherItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    @Override
    public boolean isFoil(ItemStack pStack) {
        return true;
    }
}