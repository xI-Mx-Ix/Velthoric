package net.xmx.vortex.init.registry;

import dev.architectury.registry.CreativeTabRegistry;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

public class TabRegistry {
	public static final RegistrySupplier<CreativeModeTab> XBULLET =
			ModRegistries.CREATIVE_MODE_TABS.register("vortex", () ->
					CreativeTabRegistry.create(builder -> builder
							.title(Component.literal("Vortex Physics"))
							.icon(() -> new ItemStack(Blocks.STRUCTURE_BLOCK))
							.displayItems((parameters, output) -> {
								output.accept(ItemRegistry.PHYSICS_CREATOR_STICK.get());
								output.accept(ItemRegistry.PHYSICS_REMOVER_STICK.get());
								output.accept(ItemRegistry.PHYSICS_GUN.get());
								output.accept(ItemRegistry.MAGNETIZER.get());
							})
					));

	public static void register() {
		ModRegistries.CREATIVE_MODE_TABS.register();
	}
}
