/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.chaincreator;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;

/**
 * An item used to create physics-based chains between two points in the world.
 * The logic for this item is implemented in {@link VxChainCreatorMode}
 * and handled via the Velthoric Tool API.
 *
 * @author xI-Mx-Ix
 */
public class VxChainCreatorItem extends Item {

    public VxChainCreatorItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.COMMON));
    }

    /**
     * Returns pass to allow the client-side tool event handlers to process input
     * without interference from vanilla item usage logic.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level pLevel, Player pPlayer, InteractionHand pUsedHand) {
        return InteractionResultHolder.pass(pPlayer.getItemInHand(pUsedHand));
    }
}