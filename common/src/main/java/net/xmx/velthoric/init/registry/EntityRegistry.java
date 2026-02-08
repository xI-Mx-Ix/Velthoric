/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.init.registry;

import dev.architectury.platform.Platform;
import dev.architectury.registry.client.level.entity.EntityRendererRegistry;
import dev.architectury.registry.registries.RegistrySupplier;
import dev.architectury.utils.Env;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.xmx.velthoric.core.mounting.entity.VxMountingEntity;

/**
 * This class handles the registration of entity types.
 *
 * @author xI-Mx-Ix
 */
public class EntityRegistry {

    public static final RegistrySupplier<EntityType<VxMountingEntity>> MOUNTING_ENTITY =
            ModRegistries.ENTITY_TYPES.register(
                    "mounting_entity",
                    () -> EntityType.Builder.of(VxMountingEntity::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F)
                            .noSummon()
                            .fireImmune()
                            .build("mounting_entity")
            );

    public static void register() {
        ModRegistries.ENTITY_TYPES.register();
        registerEntityRenderers();
    }

    private static void registerEntityRenderers() {
        if (Platform.getEnvironment() == Env.CLIENT) {
            EntityRendererRegistry.register(EntityRegistry.MOUNTING_ENTITY, NoopRenderer::new);
        }
    }
}
