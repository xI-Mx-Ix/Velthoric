package net.xmx.xbullet.init;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.xmx.xbullet.debug.DebugEvents;
import net.xmx.xbullet.item.physicsgun.client.PhysicsGunClientHandler;
import net.xmx.xbullet.item.physicsgun.server.PhysicsGunServerHandler;
import net.xmx.xbullet.physics.PhysicsLifecycleEvents;
import net.xmx.xbullet.physics.object.fluid.FluidManagerEvents;
import net.xmx.xbullet.physics.object.global.DetonationEvents;
import net.xmx.xbullet.physics.object.global.click.ClientSendClick;
import net.xmx.xbullet.physics.object.global.physicsobject.client.ClientPhysicsObjectManager;
import net.xmx.xbullet.physics.object.global.physicsobject.client.renderer.PhysicsObjectRenderer;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.PhysicsObjectManagerEvents;
import net.xmx.xbullet.physics.core.ClientPhysicsPauseHandler;

public class RegisterEvents {

    public static void register(IEventBus modEventBus) {

        MinecraftForge.EVENT_BUS.register(PhysicsLifecycleEvents.class);
        MinecraftForge.EVENT_BUS.register(PhysicsObjectManagerEvents.class);

        MinecraftForge.EVENT_BUS.register(DetonationEvents.class);
        MinecraftForge.EVENT_BUS.register(FluidManagerEvents.class);
        MinecraftForge.EVENT_BUS.register(PhysicsGunServerHandler.class);

    }

    public static void registerClient(IEventBus modEventBus) {

        MinecraftForge.EVENT_BUS.register(ClientPhysicsObjectManager.class);
        MinecraftForge.EVENT_BUS.register(PhysicsObjectRenderer.class);
        MinecraftForge.EVENT_BUS.register(ClientPhysicsPauseHandler.class);

        MinecraftForge.EVENT_BUS.register(ClientSendClick.class);
        MinecraftForge.EVENT_BUS.register(PhysicsGunClientHandler.class);

        MinecraftForge.EVENT_BUS.register(DebugEvents.class);
    }
}