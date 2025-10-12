/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.init.registry;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.world.item.Item;
import net.xmx.velthoric.item.PhysicsCreatorItem;
import net.xmx.velthoric.item.boxthrower.BoxThrowertem;
import net.xmx.velthoric.item.magnetizer.MagnetizerItem;
import net.xmx.velthoric.item.physicsgun.PhysicsGunItem;

public class ItemRegistry {

    public static final RegistrySupplier<Item> PHYSICS_CREATOR_STICK = ModRegistries.ITEMS.register("physics_creator", PhysicsCreatorItem::new);
    public static final RegistrySupplier<Item> PHYSICS_GUN = ModRegistries.ITEMS.register("physics_gun", PhysicsGunItem::new);
    public static final RegistrySupplier<Item> MAGNETIZER = ModRegistries.ITEMS.register("magnetizer", MagnetizerItem::new);
    public static final RegistrySupplier<Item> BOX_THROWER = ModRegistries.ITEMS.register("box_thrower", BoxThrowertem::new);

    public static void register() {
        ModRegistries.ITEMS.register();
    }
}
