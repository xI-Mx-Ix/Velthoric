/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.init.registry;

import dev.architectury.registry.CreativeTabRegistry;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * Handles the registration of creative tabs.
 *
 * @author xI-Mx-Ix
 */
public class TabRegistry {
	public static final RegistrySupplier<CreativeModeTab> VELTHORIC =
			ModRegistries.CREATIVE_MODE_TABS.register("velthoric", () ->
					CreativeTabRegistry.create(builder -> builder
							.title(Component.literal("Velthoric"))
							.icon(() -> new ItemStack(Blocks.STRUCTURE_BLOCK))
							.displayItems((parameters, output) -> {
								output.accept(ItemRegistry.PHYSICS_CREATOR_STICK.get());
								output.accept(ItemRegistry.PHYSICS_GUN.get());
								output.accept(ItemRegistry.MAGNETIZER.get());
								output.accept(ItemRegistry.BOX_THROWER.get());
								output.accept(ItemRegistry.CHAIN_CREATOR.get());
							})
					));

	public static void register() {
		ModRegistries.CREATIVE_MODE_TABS.register();
	}
}