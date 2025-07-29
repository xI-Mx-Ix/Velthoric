package net.xmx.vortex.init.registry;

import dev.architectury.registry.client.level.entity.EntityRendererRegistry;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.xmx.vortex.physics.object.riding.RidingProxyEntity;

public class EntityRegistry {

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

        EntityRendererRegistry.register(EntityRegistry.RIDING_PROXY, NoopRenderer::new);
    }
}