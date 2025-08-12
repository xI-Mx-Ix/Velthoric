package net.xmx.vortex.item.physicsgun.manager;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.xmx.vortex.item.physicsgun.packet.PhysicsGunActionPacket;
import net.xmx.vortex.network.NetworkHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PhysicsGunClientManager {

    private static final PhysicsGunClientManager INSTANCE = new PhysicsGunClientManager();

    public record ClientGrabData(UUID objectUuid, Vec3 localHitPoint) {}

    private final Map<UUID, ClientGrabData> activeGrabs = new ConcurrentHashMap<>();
    private final Set<UUID> playersTryingToGrab = ConcurrentHashMap.newKeySet();

    private boolean rotationMode = false;

    private PhysicsGunClientManager() {}

    public static PhysicsGunClientManager getInstance() {
        return INSTANCE;
    }

    public void startGrabAttempt(Player player) {
        // Sende eine Anfrage an den Server, anstatt nur den lokalen Zustand zu ändern.
        // Der Server wird den Zustand an alle Clients (einschließlich diesen) zurücksenden.
        NetworkHandler.sendToServer(new PhysicsGunActionPacket(PhysicsGunActionPacket.ActionType.START_GRAB_ATTEMPT));
    }

    public void stopGrabAttempt(Player player) {
        // Sende ebenfalls eine Anfrage an den Server.
        NetworkHandler.sendToServer(new PhysicsGunActionPacket(PhysicsGunActionPacket.ActionType.STOP_GRAB_ATTEMPT));
        this.setRotationMode(false);
    }

    public boolean isTryingToGrab(Player player) {
        return playersTryingToGrab.contains(player.getUUID());
    }

    public Set<UUID> getPlayersTryingToGrab() {
        return playersTryingToGrab;
    }

    public void updateGrabState(UUID playerUuid, @Nullable UUID objectUuid, @Nullable Vec3 localHitPoint) {
        if (objectUuid == null || localHitPoint == null) {
            activeGrabs.remove(playerUuid);
        } else {
            activeGrabs.put(playerUuid, new ClientGrabData(objectUuid, localHitPoint));
            playersTryingToGrab.remove(playerUuid);
        }
    }

    public void setFullGrabState(Map<UUID, ClientGrabData> allGrabs) {
        activeGrabs.clear();
        activeGrabs.putAll(allGrabs);
    }

    public boolean isGrabbing(Player player) {
        return activeGrabs.containsKey(player.getUUID());
    }

    public Map<UUID, ClientGrabData> getActiveGrabs() {
        return activeGrabs;
    }

    public boolean isRotationMode() {
        return this.rotationMode;
    }

    public void setRotationMode(boolean active) {
        this.rotationMode = active;
    }
}