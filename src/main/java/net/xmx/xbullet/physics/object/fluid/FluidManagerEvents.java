package net.xmx.xbullet.physics.object.fluid;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.xmx.xbullet.physics.object.global.physicsobject.IPhysicsObject;
import net.xmx.xbullet.physics.object.rigidphysicsobject.RigidPhysicsObject;
import net.xmx.xbullet.physics.physicsworld.PhysicsWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class FluidManagerEvents {

    private static final FluidManager fluidManager = new FluidManager();

    private FluidManagerEvents() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        PhysicsWorld.getAll().forEach(world -> {
            if (!world.isRunning()) {
                return;
            }

            var manager = world.getObjectManager();

            List<RigidPhysicsObject> rigidObjects = manager.getManagedObjects().values().stream()
                    .filter(RigidPhysicsObject.class::isInstance)
                    .map(RigidPhysicsObject.class::cast)
                    .toList();

            fluidManager.tickForDimension(world.getLevel(), world, rigidObjects);
        });
    }
}