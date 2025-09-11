/*
This file is part of Velthoric.
Licensed under LGPL 3.0.
*/
package net.xmx.velthoric.init.registry;

import dev.architectury.registry.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.xmx.velthoric.init.VxMainClass;

public class ModRegistries {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(VxMainClass.MODID, Registries.ITEM);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(VxMainClass.MODID, Registries.CREATIVE_MODE_TAB);

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(VxMainClass.MODID, Registries.ENTITY_TYPE);

    public static void register() {
        ItemRegistry.register();
        TabRegistry.register();
        EntityRegistry.register();
        CommandRegistry.register();
    }
}