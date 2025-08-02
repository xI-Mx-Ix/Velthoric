package net.xmx.vortex.init;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.xmx.vortex.debug.screen.DebugScreen;
import net.xmx.vortex.item.magnetizer.event.MagnetizerEvents;
import net.xmx.vortex.item.physicsgun.beam.PhysicsGunBeamRenderer;
import net.xmx.vortex.item.physicsgun.event.PhysicsGunEvents;
import net.xmx.vortex.physics.VxLifecycleEvents;
import net.xmx.vortex.physics.constraint.manager.events.ConstraintLifecycleEvents;
import net.xmx.vortex.physics.object.physicsobject.client.ClientPhysicsObjectManager;
import net.xmx.vortex.physics.object.physicsobject.client.renderer.PhysicsObjectRenderer;
import net.xmx.vortex.physics.object.physicsobject.client.time.ClientPhysicsPauseHandler;
import net.xmx.vortex.physics.object.physicsobject.manager.event.ObjectLifecycleEvents;
import net.xmx.vortex.physics.object.riding.ClientPlayerRidingSystem;

public class RegisterEvents {

    public static void register() {
        VxLifecycleEvents.registerEvents();
        ObjectLifecycleEvents.registerEvents();

        ConstraintLifecycleEvents.registerEvents();
        PhysicsGunEvents.registerEvents();
        MagnetizerEvents.registerEvents();
    }

    @Environment(EnvType.CLIENT)
    public static void registerClient() {
        ClientPlayerRidingSystem.registerEvents();

        ClientPhysicsObjectManager.registerEvents();

        ClientPhysicsPauseHandler.registerEvents();
        DebugScreen.registerEvents();

        PhysicsObjectRenderer.registerEvents();
        PhysicsGunBeamRenderer.registerEvents();
    }
}
