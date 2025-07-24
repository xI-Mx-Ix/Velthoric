package net.xmx.vortex.init.registry;

import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;
import net.xmx.vortex.physics.object.riding.RidingProxyEntity;
import net.xmx.vortex.test.ExampleCarEntity;
import net.xmx.vortex.test.ExampleCarRenderer;

@Mod.EventBusSubscriber(modid = "vortex", bus = Mod.EventBusSubscriber.Bus.MOD)
public class EntityRegistry {

    public static final RegistryObject<EntityType<ExampleCarEntity>> EXAMPLE_CAR =
            ModRegistries.ENTITY_TYPES.register(
                    "example_car",
                    () -> EntityType.Builder.of(ExampleCarEntity::new, MobCategory.MISC)
                            .sized(1.0F, 1.0F)
                            .build("example_car")
            );

    public static final RegistryObject<EntityType<RidingProxyEntity>> RIDING_PROXY =
            ModRegistries.ENTITY_TYPES.register(
                    "riding_proxy",
                    () -> EntityType.Builder.<RidingProxyEntity>of(RidingProxyEntity::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F)
                            .noSummon()
                            .fireImmune()
                            .build("riding_proxy")
            );

    @SubscribeEvent
    public static void registerEntityTypes(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EXAMPLE_CAR.get(), ExampleCarRenderer::new);
        event.registerEntityRenderer(RIDING_PROXY.get(), NoopRenderer::new);
    }

    public static void register(IEventBus eventBus) {
        ModRegistries.ENTITY_TYPES.register(eventBus);
    }
}