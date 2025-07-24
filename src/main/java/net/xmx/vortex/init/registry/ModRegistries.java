package net.xmx.vortex.init.registry;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.eventbus.api.IEventBus;
import net.xmx.vortex.init.VxMainClass;

public class ModRegistries {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, VxMainClass.MODID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, VxMainClass.MODID);

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, VxMainClass.MODID);

    public static final DeferredRegister<DimensionType> DIMENSION_TYPES =
            DeferredRegister.create(Registries.DIMENSION_TYPE, VxMainClass.MODID);


    public static void register(IEventBus eventBus) {
        EntityRegistry.register(eventBus);
        ItemRegistry.register(eventBus);
        TabRegistry.register(eventBus);
        DimensionRegistry.register(eventBus);
    }
}