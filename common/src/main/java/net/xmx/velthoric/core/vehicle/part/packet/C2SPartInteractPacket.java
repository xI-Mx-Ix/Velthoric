/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.vehicle.part.packet;

import dev.architectury.networking.NetworkManager;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.velthoric.network.IVxNetPacket;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.core.body.type.VxBody;
import net.xmx.velthoric.core.vehicle.VxVehicle;
import net.xmx.velthoric.core.vehicle.part.VxPart;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;

import java.util.UUID;

/**
 * A packet sent from client to server when a player interacts with a vehicle part.
 * Contains the Vehicle UUID and the Part UUID.
 *
 * @author xI-Mx-Ix
 */
public class C2SPartInteractPacket implements IVxNetPacket {

    private final UUID vehicleId;
    private final UUID partId;

    /**
     * Constructs a new interaction packet.
     *
     * @param vehicleId The unique identifier of the vehicle entity.
     * @param partId    The unique identifier of the specific part.
     */
    public C2SPartInteractPacket(UUID vehicleId, UUID partId) {
        this.vehicleId = vehicleId;
        this.partId = partId;
    }

    /**
     * Decodes the packet from the network buffer.
     *
     * @param buf The buffer to read from.
     * @return The decoded packet instance.
     */
    public static C2SPartInteractPacket decode(VxByteBuf buf) {
        return new C2SPartInteractPacket(buf.readUUID(), buf.readUUID());
    }

    @Override
    public void encode(VxByteBuf buf) {
        buf.writeUUID(this.vehicleId);
        buf.writeUUID(this.partId);
    }

    @Override
    public void handle(NetworkManager.PacketContext context) {
        context.queue(() -> {
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            if (player == null) return;

            VxPhysicsWorld world = VxPhysicsWorld.get(player.level().dimension());
            if (world == null) return;

            // 1. Find Vehicle
            VxBody body = world.getBodyManager().getVxBody(this.vehicleId);
            if (body instanceof VxVehicle vehicle) {
                // 2. Find Part
                VxPart part = vehicle.getPart(this.partId);

                // 3. Execute Interaction
                if (part != null) {
                    part.interact(player);
                }
            }
        });
    }
}