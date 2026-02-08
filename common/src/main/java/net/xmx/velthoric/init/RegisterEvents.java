/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.init;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.xmx.velthoric.core.body.client.VxClientBodyManager;
import net.xmx.velthoric.debug.VxF3ScreenAddition;
import net.xmx.velthoric.item.physicsgun.beam.VxPhysicsGunBeamRenderer;
import net.xmx.velthoric.item.physicsgun.event.VxPhysicsGunClientEvents;
import net.xmx.velthoric.item.physicsgun.event.VxPhysicsGunEvents;
import net.xmx.velthoric.item.tool.event.VxToolClientEvents;
import net.xmx.velthoric.item.tool.event.VxToolEvents;
import net.xmx.velthoric.core.lifecycle.VxServerLifecycleHandler;
import net.xmx.velthoric.core.body.client.renderer.dispatcher.VxPhysicsRenderDispatcher;
import net.xmx.velthoric.core.vehicle.gui.VxVehicleHudRenderer;

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
        VxClientBodyManager.registerEvents();
        VxF3ScreenAddition.registerEvents();
        VxPhysicsRenderDispatcher.registerEvents();
        VxPhysicsGunBeamRenderer.registerEvents();
        VxPhysicsGunClientEvents.registerEvents();
        VxToolClientEvents.registerEvents();
        VxVehicleHudRenderer.registerEvents();
    }
}