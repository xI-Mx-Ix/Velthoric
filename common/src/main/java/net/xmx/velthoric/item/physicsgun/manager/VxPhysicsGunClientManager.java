/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.physicsgun.manager;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.item.physicsgun.packet.VxPhysicsGunActionPacket;
import net.xmx.velthoric.network.VxPacketHandler;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VxPhysicsGunClientManager {

    private static final VxPhysicsGunClientManager INSTANCE = new VxPhysicsGunClientManager();

    public record ClientGrabData(UUID objectUuid, Vec3 localHitPoint) {}

    private final Map<UUID, ClientGrabData> activeGrabs = new ConcurrentHashMap<>();
    private final Set<UUID> playersTryingToGrab = ConcurrentHashMap.newKeySet();
    private boolean rotationMode = false;

    private VxPhysicsGunClientManager() {}

    public static VxPhysicsGunClientManager getInstance() {
        return INSTANCE;
    }

    public void startGrabAttempt() {
        VxPacketHandler.sendToServer(new VxPhysicsGunActionPacket(VxPhysicsGunActionPacket.ActionType.START_GRAB_ATTEMPT));
    }

    public void stopGrabAttempt() {
        VxPacketHandler.sendToServer(new VxPhysicsGunActionPacket(VxPhysicsGunActionPacket.ActionType.STOP_GRAB_ATTEMPT));
        this.setRotationMode(false);
    }

    public void updateState(Map<UUID, ClientGrabData> newActiveGrabs, Set<UUID> newPlayersTryingToGrab) {
        activeGrabs.clear();
        activeGrabs.putAll(newActiveGrabs);

        playersTryingToGrab.clear();
        playersTryingToGrab.addAll(newPlayersTryingToGrab);
    }

    public boolean isGrabbing(Player player) {
        return activeGrabs.containsKey(player.getUUID());
    }

    public boolean isTryingToGrab(Player player) {
        return playersTryingToGrab.contains(player.getUUID());
    }

    public Map<UUID, ClientGrabData> getActiveGrabs() {
        return activeGrabs;
    }

    public Set<UUID> getPlayersTryingToGrab() {
        return playersTryingToGrab;
    }

    public boolean isRotationMode() {
        return this.rotationMode;
    }

    public void setRotationMode(boolean active) {
        this.rotationMode = active;
    }
}
