package net.xmx.vortex.init.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.xmx.vortex.init.VxMainClass;

public class DimensionRegistry {

    public static final ResourceLocation SHIPYARD_LOCATION = ResourceLocation.tryBuild(VxMainClass.MODID, "shipyard");

    //public static final ResourceKey<Level> SHIPYARD_LEVEL_KEY = ResourceKey.create(Registries.DIMENSION, SHIPYARD_LOCATION);
    //public static final ResourceKey<DimensionType> SHIPYARD_TYPE_KEY = ResourceKey.create(Registries.DIMENSION_TYPE, SHIPYARD_LOCATION);

    public static void register(IEventBus eventBus) {
        ModRegistries.DIMENSION_TYPES.register(eventBus);
    }
}
