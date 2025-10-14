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
 * Player input is handled via Mixins and network packets, which trigger server-side logic in the {@link VxChainCreatorManager}.
 *
 * @author xI-Mx-Ix
 */
public class VxChainCreatorItem extends Item {

    public VxChainCreatorItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.COMMON));
    }

    /**
     * This method is called when the player uses the item. Returning 'pass' allows the client
     * to handle the press and release actions of the mouse button, which are captured by a Mixin.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level pLevel, Player pPlayer, InteractionHand pUsedHand) {
        return InteractionResultHolder.pass(pPlayer.getItemInHand(pUsedHand));
    }
}