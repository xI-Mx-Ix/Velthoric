package net.xmx.xbullet.physics.object.fluid;

import net.minecraft.server.level.ServerLevel;
import net.xmx.xbullet.physics.object.global.physicsobject.IPhysicsObject;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.PhysicsObjectManagerRegistry;
import net.xmx.xbullet.physics.object.rigidphysicsobject.RigidPhysicsObject;
import net.xmx.xbullet.physics.core.PhysicsWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collection;

public final class FluidManagerEvents {

    private static final FluidManager fluidManager = new FluidManager();

    private FluidManagerEvents() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        PhysicsObjectManagerRegistry.getInstance().getAllManagers().forEach((dimensionKey, manager) -> {
            if (!manager.isInitialized()) return;

            ServerLevel level = manager.getManagedLevel();
            PhysicsWorld physicsWorld = manager.getPhysicsWorld();

            if (level != null && physicsWorld != null && physicsWorld.isRunning()) {
                Collection<IPhysicsObject> allPhysObjects = manager.getManagedObjects().values();
                Collection<RigidPhysicsObject> rigidObjects = new ArrayList<>();
                for (IPhysicsObject pObj : allPhysObjects) {
                    if (pObj instanceof RigidPhysicsObject rpo) {
                        rigidObjects.add(rpo);
                    }
                }
                fluidManager.tickForDimension(level, physicsWorld, rigidObjects);
            }
        });
    }
}