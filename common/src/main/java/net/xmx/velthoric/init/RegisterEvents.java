/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.init;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.xmx.velthoric.debug.screen.DebugScreen;
import net.xmx.velthoric.item.boxthrower.event.VxBoxThrowerClientEvents;
import net.xmx.velthoric.item.boxthrower.event.VxBoxThrowerEvents;
import net.xmx.velthoric.item.chaincreator.event.VxChainCreatorClientEvents;
import net.xmx.velthoric.item.chaincreator.event.VxChainCreatorEvents;
import net.xmx.velthoric.item.magnetizer.event.VxMagnetizerClientEvents;
import net.xmx.velthoric.item.magnetizer.event.VxMagnetizerEvents;
import net.xmx.velthoric.item.physicsgun.beam.VxPhysicsGunBeamRenderer;
import net.xmx.velthoric.item.physicsgun.event.VxPhysicsGunClientEvents;
import net.xmx.velthoric.item.physicsgun.event.VxPhysicsGunEvents;
import net.xmx.velthoric.physics.VxLifecycleEvents;
import net.xmx.velthoric.physics.body.client.VxClientBodyManager;
import net.xmx.velthoric.physics.body.client.renderer.VxPhysicsRenderer;
import net.xmx.velthoric.physics.mounting.manager.VxClientMountingManager;

/**
 * @author xI-Mx-Ix
 */
public class RegisterEvents {

    public static void register() {
        VxLifecycleEvents.registerEvents();
        VxPhysicsGunEvents.registerEvents();
        VxMagnetizerEvents.registerEvents();
        VxBoxThrowerEvents.registerEvents();
        VxChainCreatorEvents.registerEvents();
    }

    @Environment(EnvType.CLIENT)
    public static void registerClient() {
        VxClientBodyManager.registerEvents();
        VxClientMountingManager.registerEvents();
        DebugScreen.registerEvents();
        VxPhysicsRenderer.registerEvents();
        VxPhysicsGunBeamRenderer.registerEvents();
        VxBoxThrowerClientEvents.registerEvents();
        VxChainCreatorClientEvents.registerEvents();
        VxMagnetizerClientEvents.registerEvents();
        VxPhysicsGunClientEvents.registerEvents();
    }
}

