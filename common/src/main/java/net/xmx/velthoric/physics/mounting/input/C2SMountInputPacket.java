/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.mounting.input;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.velthoric.physics.mounting.manager.VxMountingManager;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.function.Supplier;

/**
 * A network packet sent from the client to the server, containing the player's
 * current movement input while riding a physics-based entity.
 *
 * @author xI-Mx-Ix
 */
public class C2SMountInputPacket {

    private final VxMountInput input;

    /**
     * Constructs a new input packet with the player's ride controls.
     *
     * @param input The {@link VxMountInput} data.
     */
    public C2SMountInputPacket(VxMountInput input) {
        this.input = input;
    }

    /**
     * Encodes the packet's data into a network buffer.
     *
     * @param msg The packet instance to encode.
     * @param buf The buffer to write to.
     */
    public static void encode(C2SMountInputPacket msg, FriendlyByteBuf buf) {
        msg.input.encode(buf);
    }

    /**
     * Decodes the packet from a network buffer.
     *
     * @param buf The buffer to read from.
     * @return A new instance of the packet.
     */
    public static C2SMountInputPacket decode(FriendlyByteBuf buf) {
        return new C2SMountInputPacket(new VxMountInput(buf));
    }

    /**
     * Handles the packet on the server side.
     *
     * @param msg             The received packet.
     * @param contextSupplier A supplier for the network packet context.
     */
    public static void handle(C2SMountInputPacket msg, Supplier<NetworkManager.PacketContext> contextSupplier) {
        NetworkManager.PacketContext context = contextSupplier.get();
        context.queue(() -> {
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            if (player == null) {
                return;
            }

            VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(player.serverLevel().dimension());
            if (physicsWorld != null) {
                VxMountingManager mountingManager = physicsWorld.getMountingManager();
                mountingManager.handlePlayerInput(player, msg.input);
            }
        });
    }
}