/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.network;

import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import dev.architectury.utils.GameInstance;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.xmx.velthoric.item.boxthrower.packet.VxBoxThrowerActionPacket;
import net.xmx.velthoric.item.chaincreator.packet.VxChainCreatorActionPacket;
import net.xmx.velthoric.item.magnetizer.packet.VxMagnetizerActionPacket;
import net.xmx.velthoric.item.physicsgun.packet.VxPhysicsGunActionPacket;
import net.xmx.velthoric.item.physicsgun.packet.VxPhysicsGunSyncPacket;
import net.xmx.velthoric.physics.body.packet.batch.*;
import net.xmx.velthoric.physics.mounting.input.C2SMountInputPacket;
import net.xmx.velthoric.physics.mounting.request.C2SRequestMountPacket;
import net.xmx.velthoric.physics.vehicle.sync.S2CVehicleStatePacket;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Handles the registration and sending of all network packets for the mod.
 * This class uses Architectury's NetworkManager directly for packet handling
 * and assigns human-readable names to each packet for easier debugging.
 * @author xI-Mx-Ix
 */
public class VxPacketHandler {

    private static final String CHANNEL_NAMESPACE = "velthoric";

    /**
     * Internal record to store information about a registered packet.
     * @param id The unique ResourceLocation for the packet.
     * @param encoder The function to write the packet data to a buffer.
     * @param <T> The type of the packet message.
     */
    private record PacketInfo<T>(ResourceLocation id, BiConsumer<T, FriendlyByteBuf> encoder) {}

    private static final Map<Class<?>, PacketInfo<?>> PACKETS = new HashMap<>();

    /**
     * Registers a packet type with the network manager.
     *
     * @param type The class of the packet.
     * @param name The unique name for the packet, used as the path in its ResourceLocation.
     * @param encoder The method to encode the packet into a byte buffer.
     * @param decoder The method to decode the packet from a byte buffer.
     * @param handler The method to handle the received packet.
     * @param <T> The type of the packet.
     */
    private static <T> void registerPacket(Class<T> type,
                                           String name,
                                           BiConsumer<T, FriendlyByteBuf> encoder,
                                           Function<FriendlyByteBuf, T> decoder,
                                           BiConsumer<T, Supplier<NetworkManager.PacketContext>> handler) {
        ResourceLocation packetId = new ResourceLocation(CHANNEL_NAMESPACE, name);

        // Store the packet's ID and encoder for sending later.
        // The cast is safe because we are pairing the class type with its corresponding info.
        PACKETS.put(type, new PacketInfo<>(packetId, encoder));

        // Create a receiver that decodes the packet and then passes it to the handler.
        NetworkManager.NetworkReceiver receiver = (buf, context) -> {
            T packet = decoder.apply(buf);
            handler.accept(packet, () -> context);
        };

        // Register the receiver for client-to-server communication.
        NetworkManager.registerReceiver(NetworkManager.c2s(), packetId, receiver);
        // On the client, also register for server-to-client communication.
        if (Platform.getEnvironment() == Env.CLIENT) {
            NetworkManager.registerReceiver(NetworkManager.s2c(), packetId, receiver);
        }
    }

    /**
     * Registers all packets used by the mod.
     * This method must be called during mod initialization.
     */
    public static void register() {
        registerPacket(
                VxChainCreatorActionPacket.class,
                "chain_creator_action",
                VxChainCreatorActionPacket::encode,
                VxChainCreatorActionPacket::decode,
                VxChainCreatorActionPacket::handle
        );

        registerPacket(
                C2SRequestMountPacket.class,
                "request_mount",
                C2SRequestMountPacket::encode,
                C2SRequestMountPacket::decode,
                C2SRequestMountPacket::handle
        );

        registerPacket(
                S2CSynchronizedDataBatchPacket.class,
                "sync_data_batch",
                S2CSynchronizedDataBatchPacket::encode,
                S2CSynchronizedDataBatchPacket::decode,
                S2CSynchronizedDataBatchPacket::handle
        );

        registerPacket(
                C2SMountInputPacket.class,
                "mount_input",
                C2SMountInputPacket::encode,
                C2SMountInputPacket::decode,
                C2SMountInputPacket::handle
        );

        registerPacket(
                S2CVehicleStatePacket.class,
                "vehicle_state",
                S2CVehicleStatePacket::encode,
                S2CVehicleStatePacket::decode,
                S2CVehicleStatePacket::handle
        );

        registerPacket(
                S2CSpawnBodyBatchPacket.class,
                "spawn_body_batch",
                S2CSpawnBodyBatchPacket::encode,
                S2CSpawnBodyBatchPacket::decode,
                S2CSpawnBodyBatchPacket::handle
        );

        registerPacket(
                S2CRemoveBodyBatchPacket.class,
                "remove_body_batch",
                S2CRemoveBodyBatchPacket::encode,
                S2CRemoveBodyBatchPacket::decode,
                S2CRemoveBodyBatchPacket::handle
        );

        registerPacket(
                S2CUpdateBodyStateBatchPacket.class,
                "update_body_state_batch",
                S2CUpdateBodyStateBatchPacket::encode,
                S2CUpdateBodyStateBatchPacket::decode,
                S2CUpdateBodyStateBatchPacket::handle
        );

        registerPacket(
                S2CUpdateVerticesBatchPacket.class,
                "update_vertices_batch",
                S2CUpdateVerticesBatchPacket::encode,
                S2CUpdateVerticesBatchPacket::decode,
                S2CUpdateVerticesBatchPacket::handle
        );

        registerPacket(
                VxPhysicsGunSyncPacket.class,
                "physics_gun_sync",
                VxPhysicsGunSyncPacket::encode,
                VxPhysicsGunSyncPacket::decode,
                VxPhysicsGunSyncPacket::handle
        );

        registerPacket(
                VxMagnetizerActionPacket.class,
                "magnetizer_action",
                VxMagnetizerActionPacket::encode,
                VxMagnetizerActionPacket::decode,
                VxMagnetizerActionPacket::handle
        );

        registerPacket(
                VxPhysicsGunActionPacket.class,
                "physics_gun_action",
                VxPhysicsGunActionPacket::encode,
                VxPhysicsGunActionPacket::decode,
                VxPhysicsGunActionPacket::handle
        );

        registerPacket(
                VxBoxThrowerActionPacket.class,
                "box_thrower_action",
                VxBoxThrowerActionPacket::encode,
                VxBoxThrowerActionPacket::decode,
                VxBoxThrowerActionPacket::handle
        );
    }

    /**
     * Retrieves the stored PacketInfo for a given message object.
     * @param message The packet message instance.
     * @return The PacketInfo associated with the message's class.
     * @throws IllegalArgumentException if the packet has not been registered.
     */
    @SuppressWarnings("unchecked")
    private static <MSG> PacketInfo<MSG> getPacketInfo(MSG message) {
        // Retrieve the info and cast it to the specific message type.
        // This cast is safe due to the way PacketInfo is stored in the PACKETS map.
        PacketInfo<MSG> info = (PacketInfo<MSG>) PACKETS.get(message.getClass());
        return Objects.requireNonNull(info, "Unregistered packet type: " + message.getClass().getName());
    }

    /**
     * Sends a packet from the client to the server.
     * @param message The packet to send.
     * @param <MSG> The type of the packet.
     */
    public static <MSG> void sendToServer(MSG message) {
        PacketInfo<MSG> info = getPacketInfo(message);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        info.encoder.accept(message, buf);
        NetworkManager.sendToServer(info.id, buf);
    }

    /**
     * Sends a packet from the server to a specific player.
     * @param message The packet to send.
     * @param player The player to send the packet to.
     * @param <MSG> The type of the packet.
     */
    public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
        PacketInfo<MSG> info = getPacketInfo(message);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        info.encoder.accept(message, buf);
        NetworkManager.sendToPlayer(player, info.id, buf);
    }

    /**
     * Sends a packet to all players in a specific dimension.
     * @param message The packet to send.
     * @param dimensionKey The key of the dimension to send the packet to.
     * @param <MSG> The type of the packet.
     */
    public static <MSG> void sendToDimension(MSG message, ResourceKey<Level> dimensionKey) {
        if (GameInstance.getServer() != null) {
            ServerLevel level = GameInstance.getServer().getLevel(dimensionKey);
            if (level != null) {
                PacketInfo<MSG> info = getPacketInfo(message);
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                info.encoder.accept(message, buf);
                NetworkManager.sendToPlayers(level.players(), info.id, buf);
            }
        }
    }

    /**
     * Sends a packet to all players currently on the server.
     * @param message The packet to send.
     * @param <MSG> The type of the packet.
     */
    public static <MSG> void sendToAll(MSG message) {
        if (GameInstance.getServer() != null) {
            PacketInfo<MSG> info = getPacketInfo(message);
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            info.encoder.accept(message, buf);
            NetworkManager.sendToPlayers(GameInstance.getServer().getPlayerList().getPlayers(), info.id, buf);
        }
    }
}