/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.debug.screen;

import com.github.stephengold.joltjni.Jolt;
import com.github.stephengold.joltjni.enumerate.EBodyType;
import dev.architectury.platform.Platform;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.xmx.velthoric.event.api.VxDebugEvent;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.object.client.VxClientObjectDataStore;

import java.util.List;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public class DebugScreen {

    public static void registerEvents() {
        VxDebugEvent.AddDebugInfo.EVENT.register(DebugScreen::onDebugEvent);
    }

    public static void onDebugEvent(VxDebugEvent.AddDebugInfo event) {
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
        VxClientObjectManager clientManager = VxClientObjectManager.getInstance();
        VxClientObjectDataStore store = clientManager.getStore();

        long clientRigidCount = 0;
        long clientSoftCount = 0;
        int totalVertexCount = 0;

        for (UUID id : store.getAllObjectIds()) {
            Integer index = store.getIndexForId(id);
            if (index == null) continue;

            if (store.objectType[index] == EBodyType.RigidBody) {
                clientRigidCount++;
            } else if (store.objectType[index] == EBodyType.SoftBody) {
                clientSoftCount++;
                if (store.render_vertexData[index] != null) {
                    totalVertexCount += store.render_vertexData[index].length / 3;
                }
            }
        }

        left.add("RB: " + clientRigidCount + " | SB: " + clientSoftCount);
        left.add(String.format("Vertices: %d", totalVertexCount));
    }

    private static String getModVersion() {
        return Platform.getMod(VxMainClass.MODID).getVersion();
    }
}
