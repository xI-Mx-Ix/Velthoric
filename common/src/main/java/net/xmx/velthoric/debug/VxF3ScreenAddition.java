/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.debug;

import com.github.stephengold.joltjni.Jolt;
import dev.architectury.platform.Platform;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.xmx.velthoric.event.api.VxF3ScreenAdditionEvent;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.body.client.VxClientBodyManager;
import net.xmx.velthoric.physics.body.client.VxClientBodyDataStore;
import net.xmx.velthoric.physics.body.type.VxBody;
import net.xmx.velthoric.physics.body.type.VxRigidBody;
import net.xmx.velthoric.physics.body.type.VxSoftBody;

import java.util.List;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public class VxF3ScreenAddition {

    public static void registerEvents() {
        VxF3ScreenAdditionEvent.AddDebugInfo.EVENT.register(VxF3ScreenAddition::onDebugEvent);
    }

    public static void onDebugEvent(VxF3ScreenAdditionEvent.AddDebugInfo event) {
        List<String> infoList = event.getInfoList();
        Minecraft mc = Minecraft.getInstance();

        if (!mc.options.renderDebug || mc.level == null) {
            return;
        }

        infoList.add("");
        infoList.add("Velthoric v" + getModVersion());
        infoList.add("Jolt JNI v" + Jolt.versionString());

        addClientInfo(infoList);
    }


    private static void addClientInfo(List<String> left) {
        VxClientBodyManager clientManager = VxClientBodyManager.getInstance();
        VxClientBodyDataStore store = clientManager.getStore();

        long clientRigidCount = 0;
        long clientSoftCount = 0;

        for (UUID id : store.getAllPhysicsIds()) {
            Integer index = store.getIndexForId(id);
            if (index == null) continue;

            VxBody body = clientManager.getBody(id);
            if (body == null) continue;

            if (body instanceof VxRigidBody) {
                clientRigidCount++;
            } else if (body instanceof VxSoftBody) {
                clientSoftCount++;
            }
        }

        left.add("RB: " + clientRigidCount + " | SB: " + clientSoftCount);
    }

    private static String getModVersion() {
        return Platform.getMod(VxMainClass.MODID).getVersion();
    }
}