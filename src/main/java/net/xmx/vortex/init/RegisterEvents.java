package net.xmx.vortex.init;

import net.minecraftforge.eventbus.api.IEventBus;
import net.xmx.vortex.debug.ClientDebugEvents;
import net.xmx.vortex.debug.drawer.event.ClientShapeDrawerEvents;
import net.xmx.vortex.debug.drawer.event.ServerShapeDrawerEvents;
import net.xmx.vortex.item.magnetizer.event.MagnetizerClientEvents;
import net.xmx.vortex.item.magnetizer.event.MagnetizerEvents;
import net.xmx.vortex.item.physicsgun.beam.PhysicsGunBeamRenderer;
import net.xmx.vortex.item.physicsgun.event.PhysicsGunClientEvents;
import net.xmx.vortex.item.physicsgun.event.PhysicsGunEvents;
import net.xmx.vortex.physics.VxLifecycleEvents;
import net.xmx.vortex.physics.constraint.manager.events.ConstraintLifecycleEvents;

import net.xmx.vortex.physics.object.raycast.NotifyClientClick;
import net.xmx.vortex.physics.object.physicsobject.client.ClientPhysicsObjectManager;
import net.xmx.vortex.physics.object.physicsobject.client.renderer.PhysicsObjectRenderer;
import net.xmx.vortex.physics.object.physicsobject.manager.event.ObjectLifecycleEvents;
import net.xmx.vortex.physics.object.riding.ClientPlayerRidingSystem;
import net.xmx.vortex.physics.object.physicsobject.client.time.ClientPhysicsPauseHandler;

public class RegisterEvents {

    public static void register(IEventBus modEventBus, IEventBus forgeEventBus) {

        forgeEventBus.register(VxLifecycleEvents.class);
        forgeEventBus.register(ObjectLifecycleEvents.class);
        forgeEventBus.register(PhysicsGunEvents.class);
        forgeEventBus.register(MagnetizerEvents.class);
        forgeEventBus.register(ConstraintLifecycleEvents.class);
        forgeEventBus.register(ServerShapeDrawerEvents.class);

    }

    public static void registerClient(IEventBus modEventBus, IEventBus forgeEventBus) {

        forgeEventBus.register(ClientPhysicsObjectManager.class);
        forgeEventBus.register(PhysicsObjectRenderer.class);
        forgeEventBus.register(ClientPhysicsPauseHandler.class);
        forgeEventBus.register(NotifyClientClick.class);
        forgeEventBus.register(PhysicsGunClientEvents.class);
        forgeEventBus.register(ClientDebugEvents.class);
        forgeEventBus.register(ClientShapeDrawerEvents.class);
        forgeEventBus.register(PhysicsGunBeamRenderer.class);
        forgeEventBus.register(MagnetizerClientEvents.class);
        forgeEventBus.register(ClientPlayerRidingSystem.class);
    }
}