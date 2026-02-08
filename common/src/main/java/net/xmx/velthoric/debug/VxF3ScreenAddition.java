/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.debug;

import com.github.stephengold.joltjni.Jolt;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.xmx.velthoric.event.api.VxF3ScreenAdditionEvent;
import net.xmx.velthoric.core.body.client.VxClientBodyManager;
import net.xmx.velthoric.core.body.client.VxClientBodyDataStore;
import net.xmx.velthoric.core.body.type.VxBody;
import net.xmx.velthoric.core.body.type.VxRigidBody;
import net.xmx.velthoric.core.body.type.VxSoftBody;
import net.xmx.velthoric.core.physics.world.VxClientPhysicsWorld;

import java.util.List;
import java.util.UUID;

/**
 * A utility class for adding debug information to the F3 screen.
 * This class is used to display information about the physics engine and the
 * number of physics bodies in the game.
 *
 * @author xI-Mx-Ix
 */
@Environment(EnvType.CLIENT)
public class VxF3ScreenAddition {

    public static void registerEvents() {
        VxF3ScreenAdditionEvent.AddDebugInfo.EVENT.register(VxF3ScreenAddition::onDebugEvent);
    }

    public static void onDebugEvent(VxF3ScreenAdditionEvent.AddDebugInfo event) {
        List<String> infoList = event.getInfoList();
        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null) {
            return;
        }

        infoList.add("");
        infoList.add("Jolt JNI v" + Jolt.versionString());

        addClientInfo(infoList);
    }


    private static void addClientInfo(List<String> left) {
        VxClientBodyManager clientManager = VxClientPhysicsWorld.getInstance().getBodyManager();
        VxClientBodyDataStore store = clientManager.getStore();

        long clientRigidCount = 0;
        long clientSoftCount = 0;

        for (UUID id : store.getAllPhysicsIds()) {
            VxBody body = clientManager.getBody(id);
            if (body == null) continue;

            if (body instanceof VxRigidBody) {
                clientRigidCount++;
            } else if (body instanceof VxSoftBody) {
                clientSoftCount++;
            }
        }

        int bodyCount = store.getBodyCount();
        int capacity = store.getCapacity();
        int freeIndices = store.getFreeIndicesCount();
        long memoryBytes = store.getMemoryUsageBytes();
        String memoryString = memoryBytes > 1024 * 1024
                ? String.format("%.2f MB", memoryBytes / (1024.0 * 1024.0))
                : String.format("%d KB", memoryBytes / 1024);

        left.add(String.format("Bodies: %d (RB: %d, SB: %d)", bodyCount, clientRigidCount, clientSoftCount));
        left.add(String.format("Store: C: %d/%d | F: %d | M: %s", bodyCount, capacity, freeIndices, memoryString));
    }
}