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
     * Constructs the packet by deserializing data from a network buffer.
     *
     * @param buf The buffer to read from.
     */
    public C2SMountInputPacket(FriendlyByteBuf buf) {
        this.input = new VxMountInput(buf);
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