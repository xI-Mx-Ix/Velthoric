/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.init;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.xmx.velthoric.debug.VxF3ScreenAddition;
import net.xmx.velthoric.item.physicsgun.beam.VxPhysicsGunBeamRenderer;
import net.xmx.velthoric.item.physicsgun.event.VxPhysicsGunClientEvents;
import net.xmx.velthoric.item.physicsgun.event.VxPhysicsGunEvents;
import net.xmx.velthoric.item.tool.event.VxToolClientEvents;
import net.xmx.velthoric.item.tool.event.VxToolEvents;
import net.xmx.velthoric.physics.lifecycle.VxClientLifecycleHandler;
import net.xmx.velthoric.physics.lifecycle.VxServerLifecycleHandler;
import net.xmx.velthoric.physics.body.client.renderer.VxPhysicsRenderer;
import net.xmx.velthoric.physics.vehicle.gui.VxVehicleHudRenderer;

/**
 * @author xI-Mx-Ix
 */
public class RegisterEvents {

    public static void register() {
        VxServerLifecycleHandler.registerEvents();
        VxPhysicsGunEvents.registerEvents();
        VxToolEvents.registerEvents();
    }

    @Environment(EnvType.CLIENT)
    public static void registerClient() {
        VxClientLifecycleHandler.registerEvents();
        VxF3ScreenAddition.registerEvents();
        VxPhysicsRenderer.registerEvents();
        VxPhysicsGunBeamRenderer.registerEvents();
        VxPhysicsGunClientEvents.registerEvents();
        VxToolClientEvents.registerEvents();
        VxVehicleHudRenderer.registerEvents();
    }
}