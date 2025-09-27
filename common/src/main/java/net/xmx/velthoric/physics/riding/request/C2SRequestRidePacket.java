/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.riding.request;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.velthoric.physics.riding.manager.VxRidingManager;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * A network packet sent from the client to the server to request starting
 * to ride a specific seat on a physics object.
 *
 * @author xI-Mx-Ix
 */
public class C2SRequestRidePacket {

    private final UUID objectId;
    private final String seatName;

    /**
     * Constructs a new request to ride packet.
     *
     * @param objectId The UUID of the physics object.
     * @param seatName The name of the seat to ride.
     */
    public C2SRequestRidePacket(UUID objectId, String seatName) {
        this.objectId = objectId;
        this.seatName = seatName;
    }

    /**
     * Constructs the packet by deserializing data from a network buffer.
     *
     * @param buf The buffer to read from.
     */
    public C2SRequestRidePacket(FriendlyByteBuf buf) {
        this.objectId = buf.readUUID();
        this.seatName = buf.readUtf();
    }

    /**
     * Serializes the packet's data into a network buffer.
     *
     * @param buf The buffer to write to.
     */
    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(this.objectId);
        buf.writeUtf(this.seatName);
    }

    /**
     * Handles the packet on the server side, validating the request and initiating the ride.
     *
     * @param msg             The received packet.
     * @param contextSupplier A supplier for the network packet context.
     */
    public static void handle(C2SRequestRidePacket msg, Supplier<NetworkManager.PacketContext> contextSupplier) {
        NetworkManager.PacketContext context = contextSupplier.get();
        context.queue(() -> {
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            if (player == null) {
                return;
            }

            VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(player.serverLevel().dimension());
            if (physicsWorld != null) {
                VxRidingManager ridingManager = physicsWorld.getRidingManager();
                ridingManager.requestRiding(player, msg.objectId, msg.seatName);
            }
        });
    }
}