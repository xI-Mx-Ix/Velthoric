package net.xmx.velthoric.debug.screen;

import com.github.stephengold.joltjni.Jolt;
import com.github.stephengold.joltjni.enumerate.EBodyType;
import dev.architectury.platform.Platform;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.xmx.velthoric.event.api.VxDebugEvent;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.object.physicsobject.client.ClientObjectDataManager;

import java.util.Collection;
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
        ClientObjectDataManager clientManager = ClientObjectDataManager.getInstance();
        Collection<UUID> allClientIds = clientManager.getAllObjectIds();

        long clientRigidCount = allClientIds.stream().filter(id -> clientManager.getObjectType(id) == EBodyType.RigidBody).count();
        long clientSoftCount = allClientIds.stream().filter(id -> clientManager.getObjectType(id) == EBodyType.SoftBody).count();

        left.add("RB: " + clientRigidCount + " | SB: " + clientSoftCount);

        int rigidRenderers = clientManager.getRegisteredRigidRendererFactoryCount();
        int softRenderers = clientManager.getRegisteredSoftRendererFactoryCount();

        left.add("RB Renderers: " + rigidRenderers + " | SB Renderers: " + softRenderers);

        int totalNodeCount = clientManager.getTotalNodeCount();
        left.add(String.format("Vertices: %d", totalNodeCount));
    }

    private static String getModVersion() {
        return Platform.getMod(VxMainClass.MODID).getVersion();
    }
}