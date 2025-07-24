package net.xmx.vortex.init.registry;


import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegistryObject;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.network.chat.Component;

public class TabRegistry {
	private static Object Main;

	public static final RegistryObject<CreativeModeTab> XBULLET = ModRegistries.CREATIVE_MODE_TABS.register("vortex",
			() -> CreativeModeTab.builder()
					.title(Component.literal("Vortex Physics"))
					.icon(() -> new ItemStack(Blocks.STRUCTURE_BLOCK))
					.displayItems((parameters, tabData) -> {

				tabData.accept(ItemRegistry.PHYSICS_CREATOR_STICK.get());
				tabData.accept(ItemRegistry.PHYSICS_REMOVER_STICK.get());
				tabData.accept(ItemRegistry.PHYSICS_GUN.get());
				tabData.accept(ItemRegistry.MAGNETIZER.get());

			})
					.withSearchBar()
					.build());

	public static void register(IEventBus eventBus) {
		ModRegistries.CREATIVE_MODE_TABS.register(eventBus);
	}
}
