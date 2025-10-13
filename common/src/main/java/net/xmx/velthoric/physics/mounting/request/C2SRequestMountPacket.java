/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.mounting.request;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.velthoric.physics.mounting.manager.VxMountingManager;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * A network packet sent from client to server to request riding a specific seat,
 * identified by its unique UUID.
 *
 * @author xI-Mx-Ix
 */
public class C2SRequestMountPacket {

    private final UUID physicsId;
    private final UUID seatId;

    /**
     * Constructs a new request to ride packet.
     *
     * @param physicsId The UUID of the physics body.
     * @param seatId The UUID of the seat to ride.
     */
    public C2SRequestMountPacket(UUID physicsId, UUID seatId) {
        this.physicsId = physicsId;
        this.seatId = seatId;
    }

    /**
     * Constructs the packet by deserializing data from a network buffer.
     *
     * @param buf The buffer to read from.
     */
    public C2SRequestMountPacket(FriendlyByteBuf buf) {
        this.physicsId = buf.readUUID();
        this.seatId = buf.readUUID();
    }

    /**
     * Serializes the packet's data into a network buffer.
     *
     * @param buf The buffer to write to.
     */
    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(this.physicsId);
        buf.writeUUID(this.seatId);
    }

    /**
     * Handles the packet on the server side, validating the request and initiating the ride.
     *
     * @param msg             The received packet.
     * @param contextSupplier A supplier for the network packet context.
     */
    public static void handle(C2SRequestMountPacket msg, Supplier<NetworkManager.PacketContext> contextSupplier) {
        NetworkManager.PacketContext context = contextSupplier.get();
        context.queue(() -> {
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            if (player == null) {
                return;
            }

            VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(player.serverLevel().dimension());
            if (physicsWorld != null) {
                VxMountingManager mountingManager = physicsWorld.getMountingManager();
                mountingManager.requestMounting(player, msg.physicsId, msg.seatId);
            }
        });
    }
}