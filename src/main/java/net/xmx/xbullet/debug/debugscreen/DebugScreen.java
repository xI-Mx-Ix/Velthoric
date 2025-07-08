package net.xmx.xbullet.debug.debugscreen;

import com.github.stephengold.joltjni.Jolt;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.physics.object.physicsobject.EObjectType;
import net.xmx.xbullet.physics.object.physicsobject.client.ClientPhysicsObjectManager;

import java.util.Optional;

public class DebugScreen {

    public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.renderDebug || mc.level == null) {
            return;
        }

        event.getLeft().add("");
        event.getLeft().add("XBullet Version: " + getModVersion());
        event.getLeft().add("Jolt Physics Version:" + " " + Jolt.versionString());

        addClientInfo(event);
    }

    private static void addClientInfo(CustomizeGuiOverlayEvent.DebugText event) {
        ClientPhysicsObjectManager clientManager = ClientPhysicsObjectManager.getInstance();
        var allClientObjects = clientManager.getAllObjectData();


        long clientRigidCount = allClientObjects.stream().filter(d -> d.getObjectType() == EObjectType.RIGID_BODY).count();
        long clientSoftCount = allClientObjects.stream().filter(d -> d.getObjectType() == EObjectType.SOFT_BODY).count();

        event.getLeft().add("Client Soft Objects:" + " " + clientSoftCount);
        event.getLeft().add("Client Rigid Objects:" + " " + clientRigidCount);

        int rigidRenderers = clientManager.getRegisteredRigidRendererFactoryCount();
        int softRenderers = clientManager.getRegisteredSoftRendererFactoryCount();

        event.getLeft().add("Client Soft Renderers:" + " " + softRenderers);
        event.getLeft().add("Client Rigid Renderers:" + " " + rigidRenderers);

        int totalNodeCount = clientManager.getTotalNodeCount();
        event.getLeft().add(String.format("Total Node Count: %d", totalNodeCount));
    }

    private static String getModVersion() {
        Optional<? extends ModContainer> modContainerOpt = ModList.get().getModContainerById(XBullet.MODID);
        if (modContainerOpt.isPresent()) {
            IModInfo modInfo = modContainerOpt.get().getModInfo();
            return modInfo.getVersion().toString();
        }
        return "UNKNOWN";
    }
}
