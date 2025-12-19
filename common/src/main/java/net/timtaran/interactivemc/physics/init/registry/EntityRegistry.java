/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.init.registry;

/**
 * This class handles the registration of entity types.
 *
 * @author xI-Mx-Ix
 */
public class EntityRegistry {

    public static void register() {
        ModRegistries.ENTITY_TYPES.register();
        registerEntityRenderers();
    }

    private static void registerEntityRenderers() {
    }
}
