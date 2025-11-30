/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.init.registry;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.world.item.Item;
import net.xmx.velthoric.item.VxPhysicsCreatorItem;
import net.xmx.velthoric.item.boxlauncher.VxBoxLauncherItem;
import net.xmx.velthoric.item.boxlauncher.VxBoxLauncherMode;
import net.xmx.velthoric.item.chaincreator.VxChainCreatorItem;
import net.xmx.velthoric.item.chaincreator.VxChainCreatorMode;
import net.xmx.velthoric.item.magnetizer.VxMagnetizerItem;
import net.xmx.velthoric.item.magnetizer.VxMagnetizerMode;
import net.xmx.velthoric.item.physicsgun.VxPhysicsGunItem;
import net.xmx.velthoric.item.ragdolllauncher.VxRagdollLauncherItem;
import net.xmx.velthoric.item.ragdolllauncher.VxRagdollLauncherMode;
import net.xmx.velthoric.item.tool.registry.VxToolRegistry;

/**
 * This class handles the registration of items.
 *
 * @author xI-Mx-Ix
 */
public class ItemRegistry {
    public static final RegistrySupplier<Item> PHYSICS_CREATOR_STICK = ModRegistries.ITEMS.register("physics_creator", VxPhysicsCreatorItem::new);
    public static final RegistrySupplier<Item> PHYSICS_GUN = ModRegistries.ITEMS.register("physics_gun", VxPhysicsGunItem::new);
    public static final RegistrySupplier<Item> MAGNETIZER = ModRegistries.ITEMS.register("magnetizer", VxMagnetizerItem::new);
    public static final RegistrySupplier<Item> BOX_LAUNCHER = ModRegistries.ITEMS.register("box_launcher", VxBoxLauncherItem::new);
    public static final RegistrySupplier<Item> CHAIN_CREATOR = ModRegistries.ITEMS.register("chain_creator", VxChainCreatorItem::new);
    public static final RegistrySupplier<Item> RAGDOLL_LAUNCHER = ModRegistries.ITEMS.register("ragdoll_launcher", VxRagdollLauncherItem::new);

    public static void register() {
        ModRegistries.ITEMS.register();

        ItemRegistry.BOX_LAUNCHER.listen(item -> VxToolRegistry.register(item, new VxBoxLauncherMode()));
        ItemRegistry.MAGNETIZER.listen(item -> VxToolRegistry.register(item, new VxMagnetizerMode()));
        ItemRegistry.RAGDOLL_LAUNCHER.listen(item -> VxToolRegistry.register(item, new VxRagdollLauncherMode()));
        ItemRegistry.CHAIN_CREATOR.listen(item -> VxToolRegistry.register(item, new VxChainCreatorMode()));
    }
}