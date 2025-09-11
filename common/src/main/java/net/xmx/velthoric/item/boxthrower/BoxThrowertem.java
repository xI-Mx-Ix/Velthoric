/*
This file is part of Velthoric.
Licensed under LGPL 3.0.
*/
package net.xmx.velthoric.item.boxthrower;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;

public class BoxThrowertem extends Item {

    public BoxThrowertem() {
        super(new Properties().stacksTo(1).rarity(Rarity.RARE));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level pLevel, Player pPlayer, InteractionHand pUsedHand) {
        return InteractionResultHolder.pass(pPlayer.getItemInHand(pUsedHand));
    }

    @Override
    public boolean isFoil(ItemStack pStack) {
        return true;
    }
}