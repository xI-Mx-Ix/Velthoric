package net.xmx.velthoric.init;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.xmx.velthoric.debug.screen.DebugScreen;
import net.xmx.velthoric.item.boxthrower.event.BoxThrowerEvents;
import net.xmx.velthoric.item.magnetizer.event.MagnetizerEvents;
import net.xmx.velthoric.item.physicsgun.beam.PhysicsGunBeamRenderer;
import net.xmx.velthoric.item.physicsgun.event.PhysicsGunEvents;
import net.xmx.velthoric.physics.VxLifecycleEvents;
import net.xmx.velthoric.physics.constraint.manager.event.ConstraintLifecycleEvents;
import net.xmx.velthoric.physics.object.client.ClientObjectDataManager;
import net.xmx.velthoric.physics.object.client.renderer.VxPhysicsRenderer;
import net.xmx.velthoric.physics.object.client.time.ClientPhysicsPauseHandler;
import net.xmx.velthoric.physics.object.manager.event.ObjectLifecycleEvents;
import net.xmx.velthoric.physics.terrain.event.TerrainLifecycleEvents;

public class RegisterEvents {

    public static void register() {
        VxLifecycleEvents.registerEvents();
        ObjectLifecycleEvents.registerEvents();

        ConstraintLifecycleEvents.registerEvents();
        PhysicsGunEvents.registerEvents();
        MagnetizerEvents.registerEvents();
        BoxThrowerEvents.registerEvents();
        TerrainLifecycleEvents.registerEvents();
    }

    @Environment(EnvType.CLIENT)
    public static void registerClient() {

        ClientObjectDataManager.registerEvents();

        ClientPhysicsPauseHandler.registerEvents();
        DebugScreen.registerEvents();

        VxPhysicsRenderer.registerEvents();
        PhysicsGunBeamRenderer.registerEvents();
    }
}
