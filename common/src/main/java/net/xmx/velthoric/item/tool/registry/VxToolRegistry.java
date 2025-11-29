/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.tool.registry;

import net.minecraft.world.item.Item;
import net.xmx.velthoric.item.tool.VxToolMode;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry for associating Items with ToolModes.
 *
 * @author xI-Mx-Ix
 */
public class VxToolRegistry {

    private static final Map<Item, VxToolMode> TOOLS = new HashMap<>();

    /**
     * Registers a tool mode for a specific item.
     *
     * @param item The item to bind.
     * @param mode The tool mode logic.
     */
    public static void register(Item item, VxToolMode mode) {
        TOOLS.put(item, mode);
    }

    /**
     * Retrieves the tool mode for an item.
     *
     * @param item The item to check.
     * @return The VxToolMode, or null if not registered.
     */
    public static VxToolMode get(Item item) {
        return TOOLS.get(item);
    }
}