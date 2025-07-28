package net.xmx.vortex.init.registry;

import dev.architectury.registry.client.level.entity.EntityRendererRegistry;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.xmx.vortex.physics.object.riding.RidingProxyEntity;
import net.xmx.vortex.test.ExampleCarEntity;
import net.xmx.vortex.test.ExampleCarRenderer;

public class EntityRegistry {

    public static final RegistrySupplier<EntityType<ExampleCarEntity>> EXAMPLE_CAR =
            ModRegistries.ENTITY_TYPES.register(
                    "example_car",
                    () -> EntityType.Builder.of(ExampleCarEntity::new, MobCategory.MISC)
                            .sized(1.0F, 1.0F)
                            .build("example_car")
            );

    public static final RegistrySupplier<EntityType<RidingProxyEntity>> RIDING_PROXY =
            ModRegistries.ENTITY_TYPES.register(
                    "riding_proxy",
                    () -> EntityType.Builder.<RidingProxyEntity>of(RidingProxyEntity::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F)
                            .noSummon()
                            .fireImmune()
                            .build("riding_proxy")
            );

    public static void register() {
        ModRegistries.ENTITY_TYPES.register();

        EntityRendererRegistry.register(EntityRegistry.EXAMPLE_CAR, ExampleCarRenderer::new);
        EntityRendererRegistry.register(EntityRegistry.RIDING_PROXY, NoopRenderer::new);
    }
}