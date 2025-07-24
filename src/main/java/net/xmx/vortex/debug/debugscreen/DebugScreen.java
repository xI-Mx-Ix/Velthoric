package net.xmx.vortex.debug.debugscreen;

import com.github.stephengold.joltjni.Jolt;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;
import net.xmx.vortex.init.VxMainClass;
import net.xmx.vortex.physics.object.physicsobject.EObjectType;
import net.xmx.vortex.physics.object.physicsobject.client.ClientPhysicsObjectManager;

import java.util.Optional;

public class DebugScreen {

    public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.renderDebug || mc.level == null) {
            return;
        }

        event.getLeft().add("");
        event.getLeft().add("Vortex Physics v" + getModVersion());
        event.getLeft().add("Jolt JNI v" + Jolt.versionString());

        addClientInfo(event);
    }

    private static void addClientInfo(CustomizeGuiOverlayEvent.DebugText event) {
        ClientPhysicsObjectManager clientManager = ClientPhysicsObjectManager.getInstance();
        var allClientObjects = clientManager.getAllObjectData();


        long clientRigidCount = allClientObjects.stream().filter(d -> d.getObjectType() == EObjectType.RIGID_BODY).count();
        long clientSoftCount = allClientObjects.stream().filter(d -> d.getObjectType() == EObjectType.SOFT_BODY).count();

        event.getLeft().add("RB: " + clientRigidCount + " | SB: " + clientSoftCount);

        int rigidRenderers = clientManager.getRegisteredRigidRendererFactoryCount();
        int softRenderers = clientManager.getRegisteredSoftRendererFactoryCount();

        event.getLeft().add("RB Renderers: " + rigidRenderers + " | SB Renderers: " + softRenderers);

        int updatesPerSecond = ClientPhysicsObjectManager.getInstance().getStateUpdatesPerSecond();
        int totalNodeCount = clientManager.getTotalNodeCount();
        event.getLeft().add(String.format("Vertices: %d", totalNodeCount) + " | State Updates/s: " + updatesPerSecond);
    }

    private static String getModVersion() {
        Optional<? extends ModContainer> modContainerOpt = ModList.get().getModContainerById(VxMainClass.MODID);
        if (modContainerOpt.isPresent()) {
            IModInfo modInfo = modContainerOpt.get().getModInfo();
            return modInfo.getVersion().toString();
        }
        return "UNKNOWN";
    }
}
