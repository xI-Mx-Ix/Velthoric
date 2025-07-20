package net.xmx.xbullet.item.physicsgun.manager;

import net.minecraft.world.entity.player.Player;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PhysicsGunClientManager {

    private static final PhysicsGunClientManager INSTANCE = new PhysicsGunClientManager();
    private final Map<UUID, UUID> activeGrabs = new ConcurrentHashMap<>();
    private final Set<UUID> playersTryingToGrab = ConcurrentHashMap.newKeySet(); // NEU

    private PhysicsGunClientManager() {}

    public static PhysicsGunClientManager getInstance() {
        return INSTANCE;
    }

    public void startGrabAttempt(Player player) {
        playersTryingToGrab.add(player.getUUID());
    }

    public void stopGrabAttempt(Player player) {
        playersTryingToGrab.remove(player.getUUID());
    }

    public boolean isTryingToGrab(Player player) {
        return playersTryingToGrab.contains(player.getUUID());
    }

    public Set<UUID> getPlayersTryingToGrab() {
        return playersTryingToGrab;
    }

    public void updateGrabState(UUID playerUuid, UUID objectUuid) {
        if (objectUuid == null) {
            activeGrabs.remove(playerUuid);
        } else {
            activeGrabs.put(playerUuid, objectUuid);
        }
    }

    public void setFullGrabState(Map<UUID, UUID> allGrabs) {
        activeGrabs.clear();
        activeGrabs.putAll(allGrabs);
    }

    public boolean isGrabbing(Player player) {
        return activeGrabs.containsKey(player.getUUID());
    }

    public Map<UUID, UUID> getActiveGrabs() {
        return activeGrabs;
    }
}