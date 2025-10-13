/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.init;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.xmx.velthoric.debug.screen.DebugScreen;
import net.xmx.velthoric.item.boxthrower.event.BoxThrowerEvents;
import net.xmx.velthoric.item.magnetizer.event.MagnetizerEvents;
import net.xmx.velthoric.item.physicsgun.beam.PhysicsGunBeamRenderer;
import net.xmx.velthoric.item.physicsgun.event.PhysicsGunEvents;
import net.xmx.velthoric.physics.VxLifecycleEvents;
import net.xmx.velthoric.physics.body.client.VxClientBodyManager;
import net.xmx.velthoric.physics.body.client.renderer.VxPhysicsRenderer;
import net.xmx.velthoric.physics.mounting.manager.VxClientMountingManager;

public class RegisterEvents {

    public static void register() {
        VxLifecycleEvents.registerEvents();
        PhysicsGunEvents.registerEvents();
        MagnetizerEvents.registerEvents();
        BoxThrowerEvents.registerEvents();
    }

    @Environment(EnvType.CLIENT)
    public static void registerClient() {
        VxClientBodyManager.registerEvents();
        VxClientMountingManager.registerEvents();
        DebugScreen.registerEvents();
        VxPhysicsRenderer.registerEvents();
        PhysicsGunBeamRenderer.registerEvents();
    }
}

