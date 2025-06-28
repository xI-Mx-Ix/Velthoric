package net.xmx.xbullet.init.registry;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;
import net.xmx.xbullet.test.ExampleCarEntity;
import net.xmx.xbullet.test.ExampleCarRenderer;

@Mod.EventBusSubscriber(modid = "xbullet", bus = Mod.EventBusSubscriber.Bus.MOD)
public class EntityRegistry {

    public static final RegistryObject<EntityType<ExampleCarEntity>> EXAMPLE_CAR =
            ModRegistries.ENTITY_TYPES.register(
                    "example_car",
                    () -> EntityType.Builder.of(ExampleCarEntity::new, MobCategory.MISC)
                            .sized(1.0F, 1.0F)
                            .build("example_car")
            );

    @SubscribeEvent
    public static void registerEntityTypes(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EXAMPLE_CAR.get(), ExampleCarRenderer::new);
    }

    public static void register(IEventBus eventBus) {
        ModRegistries.ENTITY_TYPES.register(eventBus);
    }
}