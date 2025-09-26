/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.riding.input;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.velthoric.physics.riding.manager.VxRidingManager;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.function.Supplier;

/**
 * A network packet sent from the client to the server, containing the player's
 * current movement input while riding a physics-based entity.
 *
 * @author xI-Mx-Ix
 */
public class C2SRideInputPacket {

    private final VxRideInput input;

    /**
     * Constructs a new input packet with the player's ride controls.
     *
     * @param input The {@link VxRideInput} data.
     */
    public C2SRideInputPacket(VxRideInput input) {
        this.input = input;
    }

    /**
     * Constructs the packet by deserializing data from a network buffer.
     *
     * @param buf The buffer to read from.
     */
    public C2SRideInputPacket(FriendlyByteBuf buf) {
        this.input = new VxRideInput(buf);
    }

    /**
     * Serializes the packet's data into a network buffer.
     *
     * @param buf The buffer to write to.
     */
    public void encode(FriendlyByteBuf buf) {
        this.input.encode(buf);
    }

    /**
     * Handles the packet on the server side.
     *
     * @param msg             The received packet.
     * @param contextSupplier A supplier for the network packet context.
     */
    public static void handle(C2SRideInputPacket msg, Supplier<NetworkManager.PacketContext> contextSupplier) {
        NetworkManager.PacketContext context = contextSupplier.get();
        context.queue(() -> {
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            if (player == null) {
                return;
            }

            VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(player.serverLevel().dimension());
            if (physicsWorld != null) {
                VxRidingManager ridingManager = physicsWorld.getRidingManager();
                ridingManager.handlePlayerInput(player, msg.input);
            }
        });
    }
}