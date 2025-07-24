package net.xmx.vortex.init.registry;

import net.minecraft.world.item.Item;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegistryObject;
import net.xmx.vortex.item.PhysicsCreatorItem;
import net.xmx.vortex.item.PhysicsRemoverItem;
import net.xmx.vortex.item.magnetizer.MagnetizerItem;
import net.xmx.vortex.item.physicsgun.PhysicsGunItem;

public class ItemRegistry {

    public static final RegistryObject<Item> PHYSICS_CREATOR_STICK = ModRegistries.ITEMS.register("physics_creator", PhysicsCreatorItem::new);
    public static final RegistryObject<Item> PHYSICS_REMOVER_STICK = ModRegistries.ITEMS.register("physics_remover", PhysicsRemoverItem::new);

    public static final RegistryObject<Item> PHYSICS_GUN = ModRegistries.ITEMS.register("physics_gun", PhysicsGunItem::new);

    public static final RegistryObject<Item> MAGNETIZER = ModRegistries.ITEMS.register("magnetizer", MagnetizerItem::new);

    public static void register(IEventBus eventBus) {
        ModRegistries.ITEMS.register(eventBus);
    }
}
