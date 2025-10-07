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
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.object.client.renderer.VxPhysicsRenderer;
import net.xmx.velthoric.physics.riding.manager.VxClientRidingManager;

public class RegisterEvents {

    public static void register() {
        VxLifecycleEvents.registerEvents();
        PhysicsGunEvents.registerEvents();
        MagnetizerEvents.registerEvents();
        BoxThrowerEvents.registerEvents();
    }

    @Environment(EnvType.CLIENT)
    public static void registerClient() {
        VxClientObjectManager.registerEvents();
        VxClientRidingManager.registerEvents();
        DebugScreen.registerEvents();
        VxPhysicsRenderer.registerEvents();
        PhysicsGunBeamRenderer.registerEvents();
    }
}

