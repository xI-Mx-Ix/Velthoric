package net.xmx.xbullet.init;

import net.minecraftforge.eventbus.api.IEventBus;
import net.xmx.xbullet.debug.ClientDebugEvents;
import net.xmx.xbullet.item.magnetizer.event.MagnetizerClientEvents;
import net.xmx.xbullet.item.magnetizer.event.MagnetizerEvents;
import net.xmx.xbullet.item.physicsgun.beam.PhysicsGunBeamRenderer;
import net.xmx.xbullet.item.physicsgun.event.PhysicsGunClientEvents;
import net.xmx.xbullet.item.physicsgun.event.PhysicsGunEvents;
import net.xmx.xbullet.physics.PhysicsLifecycleEvents;
import net.xmx.xbullet.physics.constraint.manager.events.ConstraintLifecycleEvents;



import net.xmx.xbullet.physics.object.raycast.NotifyClientClick;
import net.xmx.xbullet.physics.object.physicsobject.client.ClientPhysicsObjectManager;
import net.xmx.xbullet.physics.object.physicsobject.client.renderer.PhysicsObjectRenderer;
import net.xmx.xbullet.physics.object.physicsobject.manager.event.ObjectLifecycleEvents;
import net.xmx.xbullet.physics.object.riding.ClientPlayerRidingSystem;
import net.xmx.xbullet.physics.object.physicsobject.client.time.ClientPhysicsPauseHandler;
import net.xmx.xbullet.physics.terrain.event.TerrainBlockEvents;
import net.xmx.xbullet.physics.terrain.event.TerrainSystemEvents;

public class RegisterEvents {

    public static void register(IEventBus modEventBus, IEventBus forgeEventBus) {

        forgeEventBus.register(PhysicsLifecycleEvents.class);

        forgeEventBus.register(ObjectLifecycleEvents.class);

        forgeEventBus.register(PhysicsGunEvents.class);
        forgeEventBus.register(TerrainSystemEvents.class);
        forgeEventBus.register(TerrainBlockEvents.class);

        forgeEventBus.register(MagnetizerEvents.class);

        forgeEventBus.register(ConstraintLifecycleEvents.class);

    }

    public static void registerClient(IEventBus modEventBus, IEventBus forgeEventBus) {

        forgeEventBus.register(ClientPhysicsObjectManager.class);
        forgeEventBus.register(PhysicsObjectRenderer.class);
        forgeEventBus.register(ClientPhysicsPauseHandler.class);

        forgeEventBus.register(NotifyClientClick.class);
        forgeEventBus.register(PhysicsGunClientEvents.class);

        forgeEventBus.register(ClientDebugEvents.class);

        forgeEventBus.register(PhysicsGunBeamRenderer.class);

        forgeEventBus.register(MagnetizerClientEvents.class);

        forgeEventBus.register(ClientPlayerRidingSystem.class);
    }
}