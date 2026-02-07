/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.bridge.mounting.input;

import dev.architectury.networking.NetworkManager;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.velthoric.bridge.mounting.manager.VxMountingManager;
import net.xmx.velthoric.network.IVxNetPacket;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

/**
 * A network packet sent from the client to the server, containing the player's
 * current movement input while riding a physics-based entity.
 *
 * @author xI-Mx-Ix
 */
public class C2SMountInputPacket implements IVxNetPacket {

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
     * Decodes the packet from a network buffer.
     *
     * @param buf The buffer to read from.
     * @return A new instance of the packet.
     */
    public static C2SMountInputPacket decode(VxByteBuf buf) {
        return new C2SMountInputPacket(new VxMountInput(buf));
    }

    @Override
    public void encode(VxByteBuf buf) {
        this.input.encode(buf);
    }

    @Override
    public void handle(NetworkManager.PacketContext context) {
        context.queue(() -> {
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            if (player == null) return;

            VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(player.serverLevel().dimension());
            if (physicsWorld != null) {
                VxMountingManager mountingManager = physicsWorld.getMountingManager();
                mountingManager.handlePlayerInput(player, this.input);
            }
        });
    }
}