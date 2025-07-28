package net.xmx.vortex.debug.screen;

import com.github.stephengold.joltjni.Jolt;
import dev.architectury.platform.Platform;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.xmx.vortex.event.api.VxDebugEvent;
import net.xmx.vortex.init.VxMainClass;
import net.xmx.vortex.physics.object.physicsobject.EObjectType;
import net.xmx.vortex.physics.object.physicsobject.client.ClientPhysicsObjectManager;

import java.util.List;

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
        infoList.add("Vortex Physics v" + getModVersion());
        infoList.add("Jolt JNI v" + Jolt.versionString());

        addClientInfo(infoList);
    }


    private static void addClientInfo(List<String> left) {
        ClientPhysicsObjectManager clientManager = ClientPhysicsObjectManager.getInstance();
        var allClientObjects = clientManager.getAllObjectData();

        long clientRigidCount = allClientObjects.stream().filter(d -> d.getObjectType() == EObjectType.RIGID_BODY).count();
        long clientSoftCount = allClientObjects.stream().filter(d -> d.getObjectType() == EObjectType.SOFT_BODY).count();

        left.add("RB: " + clientRigidCount + " | SB: " + clientSoftCount);

        int rigidRenderers = clientManager.getRegisteredRigidRendererFactoryCount();
        int softRenderers = clientManager.getRegisteredSoftRendererFactoryCount();

        left.add("RB Renderers: " + rigidRenderers + " | SB Renderers: " + softRenderers);

        int updatesPerSecond = ClientPhysicsObjectManager.getInstance().getStateUpdatesPerSecond();
        int totalNodeCount = clientManager.getTotalNodeCount();
        left.add(String.format("Vertices: %d", totalNodeCount) + " | State Updates/s: " + updatesPerSecond);
    }

    private static String getModVersion() {
        return Platform.getMod(VxMainClass.MODID).getVersion();
    }
}