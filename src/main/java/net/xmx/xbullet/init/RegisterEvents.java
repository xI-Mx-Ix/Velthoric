package net.xmx.xbullet.init;

import net.minecraftforge.eventbus.api.IEventBus;
import net.xmx.xbullet.debug.ClientDebugEvents;
import net.xmx.xbullet.item.physicsgun.event.PhysicsGunClientEvents;
import net.xmx.xbullet.item.physicsgun.event.PhysicsGunEvents;
import net.xmx.xbullet.physics.PhysicsLifecycleEvents;
import net.xmx.xbullet.physics.constraint.manager.ConstraintManagerEvents;
import net.xmx.xbullet.physics.object.fluid.FluidManagerEvents;
import net.xmx.xbullet.physics.object.global.DetonationEvents;
import net.xmx.xbullet.physics.object.global.click.ClientSendClick;
import net.xmx.xbullet.physics.object.global.physicsobject.client.ClientPhysicsObjectManager;
import net.xmx.xbullet.physics.object.global.physicsobject.client.renderer.PhysicsObjectRenderer;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.event.ObjectLifecycleEvents;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.chunk.ObjectChunkLoader;
import net.xmx.xbullet.physics.world.ClientPhysicsPauseHandler;
import net.xmx.xbullet.physics.terrain.event.TerrainBlockEvents;
import net.xmx.xbullet.physics.terrain.event.TerrainSystemEvents;

public class RegisterEvents {

    public static void register(IEventBus modEventBus, IEventBus forgeEventBus) {

        forgeEventBus.register(PhysicsLifecycleEvents.class);

        forgeEventBus.register(ObjectChunkLoader.class);
        forgeEventBus.register(ObjectLifecycleEvents.class);

        forgeEventBus.register(DetonationEvents.class);
        forgeEventBus.register(FluidManagerEvents.class);
        forgeEventBus.register(PhysicsGunEvents.class);
        forgeEventBus.register(TerrainSystemEvents.class);
        forgeEventBus.register(TerrainBlockEvents.class);
        forgeEventBus.register(ConstraintManagerEvents.class);

    }

    public static void registerClient(IEventBus modEventBus, IEventBus forgeEventBus) {

        forgeEventBus.register(ClientPhysicsObjectManager.class);
        forgeEventBus.register(PhysicsObjectRenderer.class);
        forgeEventBus.register(ClientPhysicsPauseHandler.class);

        forgeEventBus.register(ClientSendClick.class);
        forgeEventBus.register(PhysicsGunClientEvents.class);

        forgeEventBus.register(ClientDebugEvents.class);
    }
}