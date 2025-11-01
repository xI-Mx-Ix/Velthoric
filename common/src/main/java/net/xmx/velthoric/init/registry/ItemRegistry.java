/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.init.registry;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.world.item.Item;
import net.xmx.velthoric.item.VxPhysicsCreatorItem;
import net.xmx.velthoric.item.boxthrower.VxBoxThrowerItem;
import net.xmx.velthoric.item.chaincreator.VxChainCreatorItem;
import net.xmx.velthoric.item.magnetizer.VxMagnetizerItem;
import net.xmx.velthoric.item.physicsgun.VxPhysicsGunItem;

public class ItemRegistry {

    public static final RegistrySupplier<Item> PHYSICS_CREATOR_STICK = ModRegistries.ITEMS.register("physics_creator", VxPhysicsCreatorItem::new);
    public static final RegistrySupplier<Item> PHYSICS_GUN = ModRegistries.ITEMS.register("physics_gun", VxPhysicsGunItem::new);
    public static final RegistrySupplier<Item> MAGNETIZER = ModRegistries.ITEMS.register("magnetizer", VxMagnetizerItem::new);
    public static final RegistrySupplier<Item> BOX_THROWER = ModRegistries.ITEMS.register("box_thrower", VxBoxThrowerItem::new);
    public static final RegistrySupplier<Item> CHAIN_CREATOR = ModRegistries.ITEMS.register("chain_creator", VxChainCreatorItem::new);

    public static void register() {
        ModRegistries.ITEMS.register();
    }
}
