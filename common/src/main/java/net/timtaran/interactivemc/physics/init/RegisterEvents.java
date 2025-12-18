/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.init;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.timtaran.interactivemc.physics.debug.VxF3ScreenAddition;
import net.timtaran.interactivemc.physics.item.physicsgun.beam.VxPhysicsGunBeamRenderer;
import net.timtaran.interactivemc.physics.item.physicsgun.event.VxPhysicsGunClientEvents;
import net.timtaran.interactivemc.physics.item.physicsgun.event.VxPhysicsGunEvents;
import net.timtaran.interactivemc.physics.item.tool.event.VxToolClientEvents;
import net.timtaran.interactivemc.physics.item.tool.event.VxToolEvents;
import net.timtaran.interactivemc.physics.physics.VxLifecycleEvents;
import net.timtaran.interactivemc.physics.physics.body.client.VxClientBodyManager;
import net.timtaran.interactivemc.physics.physics.body.client.renderer.VxPhysicsRenderer;
import net.timtaran.interactivemc.physics.physics.mounting.manager.VxClientMountingManager;
import net.timtaran.interactivemc.physics.physics.vehicle.gui.VxVehicleHudRenderer;

/**
 * @author xI-Mx-Ix
 */
public class RegisterEvents {

    public static void register() {
        VxLifecycleEvents.registerEvents();
        VxPhysicsGunEvents.registerEvents();
        VxToolEvents.registerEvents();
    }

    @Environment(EnvType.CLIENT)
    public static void registerClient() {
        VxClientBodyManager.registerEvents();
        VxClientMountingManager.registerEvents();
        VxF3ScreenAddition.registerEvents();
        VxPhysicsRenderer.registerEvents();
        VxPhysicsGunBeamRenderer.registerEvents();
        VxPhysicsGunClientEvents.registerEvents();
        VxToolClientEvents.registerEvents();
        VxVehicleHudRenderer.registerEvents();
    }
}