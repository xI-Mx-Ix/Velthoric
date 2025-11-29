/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.tool.registry;

import net.xmx.velthoric.init.registry.ItemRegistry;
import net.xmx.velthoric.item.boxlauncher.VxBoxLauncherMode;
import net.xmx.velthoric.item.magnetizer.VxMagnetizerMode;
import net.xmx.velthoric.item.ragdolllauncher.VxRagdollLauncherMode;

/**
 * Registers tool modes.
 *
 * @author xI-Mx-Ix
 */
public class VxToolModeRegistry {
    
    public static void register() {
        VxToolRegistry.register(ItemRegistry.BOX_LAUNCHER.get(), new VxBoxLauncherMode());
        VxToolRegistry.register(ItemRegistry.MAGNETIZER.get(), new VxMagnetizerMode());
        VxToolRegistry.register(ItemRegistry.RAGDOLL_LAUNCHER.get(), new VxRagdollLauncherMode());
    }
}