package net.xmx.vortex.init.registry;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.world.item.Item;
import net.xmx.vortex.item.PhysicsCreatorItem;
import net.xmx.vortex.item.PhysicsRemoverItem;
import net.xmx.vortex.item.boxthrower.BoxThrowertem;
import net.xmx.vortex.item.magnetizer.MagnetizerItem;
import net.xmx.vortex.item.physicsgun.PhysicsGunItem;

public class ItemRegistry {

    public static final RegistrySupplier<Item> PHYSICS_CREATOR_STICK = ModRegistries.ITEMS.register("physics_creator", PhysicsCreatorItem::new);
    public static final RegistrySupplier<Item> PHYSICS_REMOVER_STICK = ModRegistries.ITEMS.register("physics_remover", PhysicsRemoverItem::new);
    public static final RegistrySupplier<Item> PHYSICS_GUN = ModRegistries.ITEMS.register("physics_gun", PhysicsGunItem::new);
    public static final RegistrySupplier<Item> MAGNETIZER = ModRegistries.ITEMS.register("magnetizer", MagnetizerItem::new);
    public static final RegistrySupplier<Item> BOX_THROWER = ModRegistries.ITEMS.register("boxthrower", BoxThrowertem::new);

    public static void register() {
        ModRegistries.ITEMS.register();
    }
}