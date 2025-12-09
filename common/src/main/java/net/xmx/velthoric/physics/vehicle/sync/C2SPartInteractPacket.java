/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle.sync;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.velthoric.physics.body.type.VxBody;
import net.xmx.velthoric.physics.vehicle.VxVehicle;
import net.xmx.velthoric.physics.vehicle.part.VxPart;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * A packet sent from client to server when a player interacts with a vehicle part.
 * Contains the Vehicle UUID and the Part UUID.
 *
 * @author xI-Mx-Ix
 */
public class C2SPartInteractPacket {

    private final UUID vehicleId;
    private final UUID partId;

    public C2SPartInteractPacket(UUID vehicleId, UUID partId) {
        this.vehicleId = vehicleId;
        this.partId = partId;
    }

    public static void encode(C2SPartInteractPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.vehicleId);
        buf.writeUUID(msg.partId);
    }

    public static C2SPartInteractPacket decode(FriendlyByteBuf buf) {
        return new C2SPartInteractPacket(buf.readUUID(), buf.readUUID());
    }

    public static void handle(C2SPartInteractPacket msg, Supplier<NetworkManager.PacketContext> ctx) {
        ctx.get().queue(() -> {
            ServerPlayer player = (ServerPlayer) ctx.get().getPlayer();
            if (player == null) return;

            VxPhysicsWorld world = VxPhysicsWorld.get(player.level().dimension());
            if (world == null) return;

            // 1. Find Vehicle
            VxBody body = world.getBodyManager().getVxBody(msg.vehicleId);
            if (body instanceof VxVehicle vehicle) {
                
                // 2. Find Part
                VxPart part = vehicle.getPart(msg.partId);
                
                // 3. Execute Interaction
                if (part != null) {
                    part.interact(player);
                }
            }
        });
    }
}